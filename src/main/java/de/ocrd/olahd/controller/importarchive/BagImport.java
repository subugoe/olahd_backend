package de.ocrd.olahd.controller.importarchive;

import de.ocrd.olahd.component.MutexFactory;
import de.ocrd.olahd.controller.ExportController;
import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.ImportResult;
import de.ocrd.olahd.domain.TrackingInfo;
import de.ocrd.olahd.domain.TrackingStatus;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.repository.mongo.TrackingRepository;
import de.ocrd.olahd.service.ArchiveManagerService;
import de.ocrd.olahd.service.PidService;
import de.ocrd.olahd.utils.Utils;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.util.FileSystemUtils;

/**
 * Class to import OCRD-ZIP in archiveManager, send metadata exportUrl to pid-Service and store
 * related information in MongoDB
 */
public class BagImport implements Runnable {

    @Autowired
    private ArchiveManagerService archiveManagerService;
    @Autowired
    private TrackingRepository trackingRepository;
    @Autowired
    private ArchiveRepository archiveRepository;
    @Autowired
    private PidService pidService;
    @Autowired
    private MutexFactory<String> mutexFactory;

    private BagImportParams params;

    private String exportUrl;

    private static final Logger logger = LoggerFactory.getLogger(BagImport.class);

    private BagImport() {
        super();
    }

    /**
     * Create the bean with factory method instead of constructor to be able to inject the services
     *
     * @param factory
     * @param params
     * @return
     */
    public static BagImport create(AutowireCapableBeanFactory factory, BagImportParams params) {
        BagImport res = new BagImport();
        factory.autowireBean(res);
        res.params = params;

        // URL where the stored file will be available after completed import
        WebMvcLinkBuilder linkBuilder = WebMvcLinkBuilder.linkTo(
            WebMvcLinkBuilder.methodOn(ExportController.class).export(params.pid, false)
        );
        res.exportUrl = linkBuilder.toString();
        return res;
    }

    @Override
    public void run() {
        ImportResult importResult = null;
        String prevPid = params.formParams.getPrev();

        try {
            /* TODO: think about no retries. There are cases (pid service or cdstar temporarily not available) when it
             * makes sense just to try again, but I thinks most of the time multiple tries would fail too */
            if (prevPid != null) {
                importResult = Failsafe.with(ImportUtils.RETRY_POLICY).get(
                    () -> archiveManagerService.importZipFile(
                        params.destination,
                        params.pid,
                        params.bagInfos,
                        prevPid
                    )
                );
            } else {
                importResult = Failsafe.with(ImportUtils.RETRY_POLICY).get(
                    () -> archiveManagerService.importZipFile(
                        params.destination,
                        params.pid,
                        params.bagInfos
                    )
                );
            }

            List<AbstractMap.SimpleImmutableEntry<String, String>> metaData = importResult
                .getMetaData();

            if (prevPid != null) {
                metaData.add(new AbstractMap.SimpleImmutableEntry<>("PREVIOUS-VERSION", prevPid));
            }

            /*
             * Send metadata and URL to PID-Service. (Use update instead of append to save 1 HTTP
             * call to the PID Service)
             */
            metaData.addAll(params.bagInfos);
            metaData.add(new AbstractMap.SimpleImmutableEntry<>("URL", exportUrl));
            pidService.updatePid(params.pid, metaData);

            if (prevPid != null) {
                // Update the old PID to link to the new version
                List<AbstractMap.SimpleImmutableEntry<String, String>> pidAppendedData = new ArrayList<>();
                pidAppendedData
                    .add(new AbstractMap.SimpleImmutableEntry<>("NEXT-VERSION", params.pid));
                pidService.appendData(prevPid, pidAppendedData);
            }

            params.info.setStatus(TrackingStatus.SUCCESS);
            params.info.setMessage("Data has been successfully imported.");
            trackingRepository.save(params.info);

            String checksumPayloadmanifest = ImportUtils.generatePayloadmanifestChecksum(params.destination);
            String ocrdIdentifier = ImportUtils.readOcrdIdentifier(params.bagInfos);
            // New archive in mongoDB for this import
            Archive archive = new Archive(
                params.pid, importResult.getOnlineId(), importResult.getOfflineId(), ocrdIdentifier,
                checksumPayloadmanifest
            );
            if (prevPid != null) {
                /*
                 * - this block finds the prevVersion-Archive in mongoDB, links between it and the
                 * current uploaded archive and removes its onlineId so that ... I don't know why
                 * that yet - synchronized because it could happen that two imports occur at the
                 * same time and both change the same prevVersion-Archive
                 */
                synchronized (mutexFactory.getMutex(prevPid)) {
                    Archive prevVersion = archiveRepository.findByPid(prevPid);
                    archive.setPreviousVersion(prevVersion);
                    prevVersion.setOnlineId(null);
                    prevVersion.addNextVersion(archive);
                    archiveRepository.save(archive);
                    archiveRepository.save(prevVersion);
                }
            } else {
                archiveRepository.save(archive);
            }
            sendToElastic(params.formParams.getPrev());
        } catch (Exception ex) {
            logger.error("Archive Import failed", ex);
            handleFailedImport(ex, params.pid, importResult, params.info);
        } finally {
            // Clean up the temp: Files are saved in CDStar and not needed any more
            FileSystemUtils.deleteRecursively(params.tempDir.toFile());
        }
    }

    /**
     * Inform web-notifier about the new ocrd-zip so that it can put it into the search-index.
     *
     * @param conf Config read from bag-info.txt and possibly updated with form parameters. Must not be null
     */
    private void sendToElastic(String prevPid) {
        String pid = this.params.pid;

        try {
            waitTillMetsAvailable(this.params.pid);
        } catch (FailsafeException e) {
            logger.error("Error waiting for mets.xml availability for pid: '" + pid + "'", e);
            return;
        }

        try {
            final String json = String.format(
                "{"
                    + "\"document\":\"%s\", \"context\":\"ocrd\", \"product\":\"olahds\","
                    + "\"prev\":\"%s\""
                + "}",
                pid, prevPid != null ? prevPid : ""
            );

            Utils.logDebug("Sending json to mets-Importer: '" + json + "'");

            RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse(
                    "application/json; charset=utf-8"
                ), json
            );

            Request request = new Request.Builder().url(this.params.webnotifierUrl)
                .addHeader("Accept", "*/*").addHeader("Content-Type", "application/json")
                .addHeader("Cache-Control", "no-cache").post(body).build();

            try (Response response = new OkHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error(
                        "Request to web-notifier failed. Message: '{}'. Code: '{}'",
                        response.message(), response.code()
                    );
                } else {
                    logger.info("Successfully sent request to web-notifier for PID: '" + pid + "'");
                }
            }
        } catch (Exception e) {
            logger.error("Error while trying to send request to web-notifier", e);
        }
    }

    /**
     * This method tries to read the mets for the provided pid until it is available but not longer
     * than 'seconds'.
     *
     * @param pid
     * @param seconds
     */
    private void waitTillMetsAvailable(String pid) {
        RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
            .withBackoff(5, 120, ChronoUnit.SECONDS)
            .withMaxRetries(-1)
            .withMaxDuration(Duration.ofMinutes(10));

        Failsafe.with(retryPolicy).run(() -> {
            Map<String, String> bagInfoMap = archiveManagerService.getBagInfoTxt(pid);
            String metsPath = Utils.getMetsPath(bagInfoMap);
            try (Response response = archiveManagerService.exportFile(pid, metsPath)) {
                if (response.isSuccessful()) {
                    return;
                } else {
                    throw new Exception("Failed to export mets");
                }
            }
        });
    }

    /**
     * Clean up a failed import as good as possible
     *
     * When an import fails, an exception is raised. After caching this function is called. It tries
     * to delete the pid (it was created before importing), delete stuff possibly saved to Cdstar
     * and set the information about the failure to the tracking database
     *
     * @param ex
     * @param pid
     * @param importResult
     * @param info
     */
    private void handleFailedImport(
        Exception ex, String pid, ImportResult importResult, TrackingInfo info
    ) {
        // Delete the PID
        try {
            pidService.deletePid(pid);
        } catch (Exception e) {
            logger.error(
                "error cleaning up. pid: '{}', online-id: '{}', offline-id: '{}' - {}", pid,
                importResult.getOnlineId(), importResult.getOfflineId(), e,
                "Deleting PID failed"
            );
        }

        // Delete the archives (hot and cold)
        if (importResult != null) {
            try {
                archiveManagerService.deleteArchive(importResult.getOnlineId(), null);
            } catch (Exception e) {
                logger.error(
                    "error cleaning up. pid: '{}', online-id: '{}', offline-id: '{}' - {}",
                    pid, importResult.getOnlineId(), importResult.getOfflineId(), e,
                    "Deleting online archive failed"
                );
            }
            try {
                archiveManagerService.deleteArchive(importResult.getOfflineId(), null);
            } catch (Exception e) {
                logger.error(
                    "error cleaning up. pid: '{}', online-id: '{}', offline-id: '{}' - {}",
                    pid, importResult.getOnlineId(), importResult.getOfflineId(), e,
                    "Deleting offline archive failed"
                );
            }
        }

        // Save the failure data to the tracking database
        info.setStatus(TrackingStatus.FAILED);
        info.setMessage(ex.getMessage());

        // Delete the PID in the tracking database
        info.setPid(null);

        trackingRepository.save(info);
    }
}
