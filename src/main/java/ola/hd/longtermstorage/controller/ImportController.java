package ola.hd.longtermstorage.controller;

import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.domain.Metadata;
import gov.loc.repository.bagit.exceptions.CorruptChecksumException;
import gov.loc.repository.bagit.exceptions.FileNotInPayloadDirectoryException;
import gov.loc.repository.bagit.exceptions.InvalidBagitFileFormatException;
import gov.loc.repository.bagit.exceptions.InvalidPayloadOxumException;
import gov.loc.repository.bagit.exceptions.MaliciousPathException;
import gov.loc.repository.bagit.exceptions.MissingBagitFileException;
import gov.loc.repository.bagit.exceptions.MissingPayloadDirectoryException;
import gov.loc.repository.bagit.exceptions.MissingPayloadManifestException;
import gov.loc.repository.bagit.exceptions.UnparsableVersionException;
import gov.loc.repository.bagit.exceptions.UnsupportedAlgorithmException;
import gov.loc.repository.bagit.exceptions.VerificationException;
import gov.loc.repository.bagit.reader.BagReader;
import gov.loc.repository.bagit.verify.BagVerifier;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.ResponseHeader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ola.hd.longtermstorage.Constants;
import ola.hd.longtermstorage.component.ExecutorWrapper;
import ola.hd.longtermstorage.component.MutexFactory;
import ola.hd.longtermstorage.domain.Archive;
import ola.hd.longtermstorage.domain.ImportResult;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.domain.TrackingInfo;
import ola.hd.longtermstorage.domain.TrackingStatus;
import ola.hd.longtermstorage.exceptions.OcrdzipInvalidException;
import ola.hd.longtermstorage.repository.mongo.ArchiveRepository;
import ola.hd.longtermstorage.repository.mongo.TrackingRepository;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import ola.hd.longtermstorage.service.PidService;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.ServletWebRequest;
import springfox.documentation.annotations.ApiIgnore;

@Api(description = "This endpoint is used to import a ZIP file into the system")
@RestController
public class ImportController {

    private static final Logger logger = LoggerFactory.getLogger(ImportController.class);

    private final ArchiveManagerService archiveManagerService;

    private final TrackingRepository trackingRepository;

    private final ArchiveRepository archiveRepository;

    private final PidService pidService;

    private final ExecutorWrapper executor;

    private final MutexFactory<String> mutexFactory;

    @Value("${ola.hd.upload.dir}")
    private String uploadDir;

    @Value("${webnotifier.url}")
    private String webnotifierUrl;

    @Autowired
    public ImportController(ArchiveManagerService archiveManagerService, TrackingRepository trackingRepository, ArchiveRepository archiveRepository, PidService pidService,
                            ExecutorWrapper executor, MutexFactory<String> mutexFactory) {
        this.archiveManagerService = archiveManagerService;
        this.trackingRepository = trackingRepository;
        this.archiveRepository = archiveRepository;
        this.pidService = pidService;
        this.executor = executor;
        this.mutexFactory = mutexFactory;
    }

    @ApiOperation(value = "Import a ZIP file into a system. It may be an independent ZIP, or a new version of another ZIP. " +
            "In the second case, a PID of the previous ZIP must be provided.",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            authorizations = {
                    @Authorization(value = "basicAuth"),
                    @Authorization(value = "bearer")
            })
    @ApiResponses(value = {
            @ApiResponse(code = 202, message = "The ZIP has a valid BagIt structure. The system is saving it to the archive.",
                    response = ResponseMessage.class,
                    responseHeaders = {
                            @ResponseHeader(name = "Location", description = "The PID of the ZIP.", response = String.class)
                    }),
            @ApiResponse(code = 400, message = "The ZIP has an invalid BagIt structure.", response = ResponseMessage.class),
            @ApiResponse(code = 401, message = "Invalid credentials.", response = ResponseMessage.class),
            @ApiResponse(code = 415, message = "The request is not a multipart request.", response = ResponseMessage.class)
    })
    @ApiImplicitParams(value = {
            @ApiImplicitParam(dataType = "__file", name = "file", value = "The file to be imported.", required = true, paramType = "form"),
            @ApiImplicitParam(dataType = "string", name = "prev", value = "The PID of the previous version", paramType = "form")
    })
    @ResponseStatus(value = HttpStatus.ACCEPTED)
    @PostMapping(
            value = "/bag",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> importData(HttpServletRequest request, @ApiIgnore Principal principal)
    throws IOException, FileUploadException {
        // **every** Import-Request creates an info in tracking-database-table
        TrackingInfo info = new TrackingInfo(principal.getName(), TrackingStatus.PROCESSING,
                "Processing...", null);

        if (!ServletFileUpload.isMultipartContent(request)) {
            exitUploadWithExeption("The request must be multipart request.", info,
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        String tempDir = uploadDir + File.separator + UUID.randomUUID();

        Pair<File, String> requestParams = readParams(request, info, tempDir);

        File targetFile = requestParams.getLeft();  // The uploaded file (ZIP)
        String prev = requestParams.getRight();  // A PID pointing to the previous version
        String destination = tempDir + File.separator + FilenameUtils.getBaseName(
                targetFile.getName()) + "_extracted";

        List<AbstractMap.SimpleImmutableEntry<String, String>> bagInfos = extractAndVerifyOcrdzip(
                targetFile, destination, tempDir, info);

        // Retry policies when a call to another service is failed
        RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
                .withDelay(Duration.ofSeconds(10))
                .withMaxRetries(3);

        // Create a PID with meta-data from bag-info.txt
        String pid = Failsafe.with(retryPolicy).get(() -> pidService.createPid(bagInfos));

        // Store the PID to the tracking database
        info.setPid(pid);

        // **here the OCRD-ZIP is saved** to the external Archive
        executor.submit(new BagImport(destination, pid, prev, bagInfos, info, tempDir));

        trackingRepository.save(info);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                sendToElastic(pid, archiveRepository);
            }
        });

        ResponseMessage responseMessage = new ResponseMessage(HttpStatus.ACCEPTED,
                "Your data is being processed.");
        responseMessage.setPid(pid);

        return ResponseEntity.accepted().body(responseMessage);
    }

    /**
     * Class to store OCRD-ZIP in archiveManager, send metadata exportUrl to pid-Service and store
     * related information in MongoDB
     */
    private class BagImport implements Runnable {
        private String destination;
        private String pid;
        private String prevPid;
        private String exportUrl;
        private List<AbstractMap.SimpleImmutableEntry<String, String>> bagInfos;
        private TrackingInfo info;
        private String tempDir;
        private RetryPolicy<Object> retryPolicy = new RetryPolicy<>()
                .withDelay(Duration.ofSeconds(10))
                .withMaxRetries(3);

        /**
         * {@linkplain BagImport}.
         *
         * This Constructor accepts parameters from external context needed for the execution of the
         * functionality
         *
         * @param destination path to extracted OCRD-ZIP
         * @param pid         PID of currently uploaded OCRD-ZIP
         * @param prevPid     PID of previously uploaded version OCRD-ZIP
         * @param exportUrl   URL to retrieve the newly uploaded OCRD-ZIP
         * @param bagInfos    metadata for OCRD-ZIP
         * @param info        information stored about upload: success, failure etc.
         * @param tempDir     ZIP-file was stored here in the beginning of import
         */
        private BagImport(String destination, String pid, String prevPid,
                List<SimpleImmutableEntry<String, String>> bagInfos, TrackingInfo info,
                String tempDir) {
            super();
            this.destination = destination;
            this.pid = pid;
            this.prevPid = prevPid;
            this.bagInfos = bagInfos;
            this.tempDir = tempDir;
            this.info = info;

            // URL where the stored file will be available after completed import
            WebMvcLinkBuilder linkBuilder = WebMvcLinkBuilder.linkTo(
                    WebMvcLinkBuilder.methodOn(ExportController.class).export(pid, false));
            this.exportUrl = linkBuilder.toString();

        }

        @Override
        public void run() {
            ImportResult importResult = null;
            try {
                if (prevPid != null) {
                    importResult = Failsafe.with(retryPolicy).get(
                            () -> archiveManagerService.importZipFile(Paths.get(destination), pid,
                                    bagInfos, prevPid));
                } else {
                    importResult = Failsafe.with(retryPolicy)
                            .get(() -> archiveManagerService.importZipFile(Paths.get(destination),
                                    pid, bagInfos));
                }

                List<AbstractMap.SimpleImmutableEntry<String, String>> metaData = importResult.
                        getMetaData();

                if (prevPid != null) {
                    metaData.add(new AbstractMap.SimpleImmutableEntry<>("PREVIOUS-VERSION",
                            prevPid));
                }

                /* Send metadata and URL to PID-Service. (Use update instead of append to save 1
                 * HTTP call to the PID Service) */
                metaData.addAll(bagInfos);
                metaData.add(new AbstractMap.SimpleImmutableEntry<>("URL", exportUrl));
                pidService.updatePid(pid, metaData);

                if (prevPid != null) {
                    // Update the old PID to link to the new version
                    List<AbstractMap.SimpleImmutableEntry<String, String>> pidAppendedData =
                            new ArrayList<>();
                    pidAppendedData.add(new AbstractMap.SimpleImmutableEntry<>("NEXT-VERSION", pid));
                    pidService.appendData(prevPid, pidAppendedData);
                }

                info.setStatus(TrackingStatus.SUCCESS);
                info.setMessage("Data has been successfully imported.");
                trackingRepository.save(info);

                // New archive in mongoDB for this import
                Archive archive = new Archive(pid, importResult.getOnlineId(),
                        importResult.getOfflineId());
                setSearchindexFilegrps(archive, destination, bagInfos);
                if (prevPid != null) {
                    /* - this block finds the prevVersion-Archive in mongoDB, links between it and
                     *   the current uploaded archive and removes its onlineId so that ... I don't
                     *   know why that yet
                     * - synchronized because it could happen that two imports occur at the same
                     *   time and both change the same prevVersion-Archive */
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
            } catch (Exception ex) {
                handleFailedImport(ex, pid, importResult, info);
            } finally {
                // Clean up the temp: Files are saved in CDStar and not needed any more
                FileSystemUtils.deleteRecursively(new File(tempDir));
            }
        }

        private void handleFailedImport(Exception ex, String pid, ImportResult importResult,
                TrackingInfo info) {
            try {
                // Delete the PID
                pidService.deletePid(pid);

                // Delete the archive
                if (importResult != null) {
                    archiveManagerService.deleteArchive(importResult.getOnlineId(), null);
                    archiveManagerService.deleteArchive(importResult.getOfflineId(), null);
                }

            } catch (IOException e) {
                // if cleaning fails, nothing can be done than manually clean up
                logger.error("error cleaning up. pid: '{}', online-id: '{}', offline-id: '{}'", pid,
                        importResult.getOnlineId(), importResult.getOfflineId(), e);
            }

            logger.error(ex.getMessage(), ex);

            // Save the failure data to the tracking database
            info.setStatus(TrackingStatus.FAILED);
            info.setMessage(ex.getMessage());

            // Delete the PID in the tracking database
            info.setPid(null);

            trackingRepository.save(info);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex, ServletWebRequest request) {

        // Extract necessary information
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = ex.getMessage();
        String uri = request.getRequest().getRequestURI();

        // Log the error
        logger.error(message, ex);

        return ResponseEntity.badRequest()
                .body(new ResponseMessage(status, message, uri));
    }

    /**
     * Inform web-notifier about the new ocrd-zip so that it can put it into the search-index.
     *
     * FIXME: Problem: The indexer could be faster than CDStar: It tries to receive the mets before
     * it is available in CDStar. Because of that this function sleeps some time before it executes.
     * Therefore it should be run in a thread as it is done here. It could be cleaner to try to read
     * it in a loop with a timeout and send after that to elastic or throw Exception
     *
     *
     * @param pid - PID(PPA) of ocrd-zip
     */
    private void sendToElastic(String pid, ArchiveRepository archiveRepository) {
        /* TODO: remove: PID must never be null. Exception must be thrown at first possible
         * occurrence. Search for first possible occurrence and throw Exception there if necessary*/
        if (StringUtils.isBlank(pid)) {
            logger.error("pid is null, cannot send to ElasticSearch");
            return;
        }
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Archive archiv = archiveRepository.findByPid(pid);
        String imageFileGrp = ObjectUtils.firstNonNull(archiv.getImageFileGrp(), Constants.DEFAULT_IMAGE_FILEGRP);
        String fulltextFileGrp = ObjectUtils.firstNonNull(archiv.getFulltextFileGrp(), Constants.DEFAULT_FULLTEXT_FILEGRP);

        try {
            final String json = String.format(
                    "{"
                    + "\"document\":\"%s\", \"context\":\"ocrd\", \"product\":\"olahds\","
                    + "\"imageFileGrp\":\"%s\", \"fulltextFileGrp\":\"%s\", \"ftype\":\"PAGEXML_1\""
                    + "}", pid, imageFileGrp, fulltextFileGrp);

            RequestBody body = RequestBody.create(okhttp3.MediaType.parse(
                    "application/json; charset=utf-8"), json);

            Request request = new Request.Builder().url(webnotifierUrl)
                    .addHeader("Accept", "*/*").addHeader("Content-Type", "application/json")
                    .addHeader("Cache-Control", "no-cache").post(body).build();

            try (Response response = new OkHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Request to web-notifier failed. Message: '{}'. Code: '{}'",
                            response.message(), response.code());
                } else {
                    logger.debug("Sending request to web-notifier successfull");
                }
            }
        } catch (Exception e) {
            logger.error("Error while trying to send request to web-notifier", e);
        }
    }

    /**
     * Read request parameters.
     *
     * The import request must contain one ZIP and can (optionally) contain a PID of a previously
     * stored ZIP. This Method reads the ZIP-file and saves it to the temporary-directory.
     * Additionally it reads and returns the PID of a previous work if it is provided. If there is
     * not exactly one ZIP-file provided in the request a HttpClientErrorException is thrown
     *
     * @param request   request is needed to get the parameters
     * @param principal user who initiated request. User name is needed for potential error messages
     * @param tempDir   Temporary directory to store the ZIP-file in
     * @return
     * @throws IOException              forwarded from apache-commons
     * @throws FileUploadException      forwarded from apachecommons
     * @throws HttpClientErrorException if not exactly one ZIP-file is provided, or when io-error
     *                                  occurred while writing to temporary-directory
     */
    private Pair<File, String> readParams(HttpServletRequest request, TrackingInfo info,
            String tempDir)
    throws FileUploadException, IOException {
        MutablePair<File, String> res = new MutablePair<>(null, null);
        File targetFile = null;

        // 'fileCount' to make sure that there is only 1 file uploaded
        int fileCount = 0;
        FileItemIterator iterStream = new ServletFileUpload().getItemIterator(request);
        while (iterStream.hasNext()) {
            FileItemStream item = iterStream.next();

            // Is it a file?
            if (!item.isFormField()) {
                if (fileCount > 1) {
                    FileSystemUtils.deleteRecursively(new File(tempDir));
                    exitUploadWithExeption("Only 1 zip file is allowed.", info,
                            HttpStatus.BAD_REQUEST);
                } else {
                    fileCount += 1;
                }

                targetFile = new File(tempDir + File.separator + item.getName());
                res.setLeft(targetFile);
                try (InputStream uploadedStream = item.openStream();
                     OutputStream out = FileUtils.openOutputStream(targetFile)) {
                    IOUtils.copy(uploadedStream, out);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "The upload process was interrupted. Please try again.");
                }
            } else {
                // a request can be an update of an existing ZIP. In this case prevPID is provided
                try (InputStream stream = item.openStream()) {
                    String formFieldName = item.getFieldName();
                    if (formFieldName.equals("prev")) {
                        res.setRight(Streams.asString(stream));
                    }
                }
            }
        }
        if (targetFile == null) {
            exitUploadWithExeption("The request must contain 1 zip file.", info,
                    HttpStatus.BAD_REQUEST);
        }

        // Not a ZIP file?
        Tika tika = new Tika();
        String mimeType = tika.detect(targetFile);
        if (!mimeType.equals("application/zip")) {
            // Clean up the temporary directory
            FileSystemUtils.deleteRecursively(new File(tempDir));
            exitUploadWithExeption("The file must be in the ZIP format", info,
                    HttpStatus.BAD_REQUEST);
        }

        return res;
    }

    /**
     * Extract bagit, read metadata and verify that the ZIP-file is a valid bagit. Additionally
     * validate that bag is valid according to ocrd-zip: https://ocr-d.de/en/spec/ocrd_zip.
     *
     *
     * @param targetFile location of the ZIP-File
     * @param destination where to extract the file
     * @param tempDir Temporary directory to store the ZIP-file in. Needed in case of Exception to
     *        clean up
     * @param info needed to set error tracking info in case bag is not valid
     * @return
     * @throws IOException
     */
    private List<AbstractMap.SimpleImmutableEntry<String, String>> extractAndVerifyOcrdzip(
            File targetFile, String destination, String tempDir, TrackingInfo info)
    throws IOException {
        Bag bag;
        try (BagVerifier verifier = new BagVerifier(); ZipFile zipFile = new ZipFile(targetFile)) {
            // Extract the zip file
            zipFile.extractAll(destination);

            // Validate the bag
            Path rootDir = Paths.get(destination);
            BagReader reader = new BagReader();

            // Create a bag from an existing directory
            bag = reader.read(rootDir);

            if (BagVerifier.canQuickVerify(bag)) {
                BagVerifier.quicklyVerify(bag);
            }

            // Check for the validity and completeness of a bag
            verifier.isValid(bag, true);

            validateOcrdzip(bag);
        } catch (NoSuchFileException | MissingPayloadManifestException
                | UnsupportedAlgorithmException | CorruptChecksumException | MaliciousPathException
                | InvalidPayloadOxumException | FileNotInPayloadDirectoryException
                | MissingPayloadDirectoryException | InvalidBagitFileFormatException
                | InterruptedException | ZipException | UnparsableVersionException
                | MissingBagitFileException | VerificationException | OcrdzipInvalidException ex) {

            // Clean up the temp
            FileSystemUtils.deleteRecursively(new File(tempDir));

            String message = "Invalid file input. The uploaded file must be a ZIP file with BagIt "
                    + "structure.";

            // Save to the tracking database
            info.setStatus(TrackingStatus.FAILED);
            info.setMessage(message);
            trackingRepository.save(info);

            if (ex.getClass().equals(OcrdzipInvalidException.class)) {
                message = "Not a valid Ocrd-Zip: "
                        + StringUtils.join(((OcrdzipInvalidException)ex).getErrors(), ", ");
            }
            // Throw a friendly message to the client
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, message);
        }
        return bag.getMetadata().getAll();
    }

    /**
     * If an unrecoverable error occurs this method saves the error in the tracking-database and
     * throws a (Runtime)Exception
     *
     * @param msg reason of failure
     * @param info template for tracking-info
     * @param status http-status-code to return
     */
    private void exitUploadWithExeption(String msg, TrackingInfo info, HttpStatus status) {
        info.setStatus(TrackingStatus.FAILED);
        info.setMessage(msg);
        trackingRepository.save(info);
        throw new HttpClientErrorException(status, msg);
    }

    /**
     * Validates a bag against ocrd-zip specification: https://ocr-d.de/en/spec/ocrd_zip. This
     * function assumes path is existing and path is a valid extracted bagit. Because of that these
     * checks (validate the bagit) have to be done already.
     *
     * Many, even from ocr-d provided testdata, does not fully follow the specification. So some
     * checks are disabled for now.
     *
     * @param bag
     * @param bagInfos
     * @throws OcrdzipInvalidException - if bag is invalid
     */
    private static void validateOcrdzip(Bag bag) throws OcrdzipInvalidException {
        List<String> res = new ArrayList<>();
        Metadata metadata = bag.getMetadata();
//        if (!metadata.contains("BagIt-Profile-Identifier")) {
//            res.add("bag-info.txt must contain key: 'BagIt-Profile-Identifier'");
//            // this identifier has no impact of the anything regarding this bagit and it's only
//            // purpose is to reference the spec. So verification does not help ensure functionality
//        }
//        if (!metadata.contains("Ocrd-Identifier")) {
//            res.add("bag-info.txt must contain key: 'Ocrd-Identifier'");
//            // spec says "A globally unique identifier" but I have no idea how to verify that
//        }
//        if (!metadata.contains("Ocrd-Base-Version-Checksum")) {
//            res.add("bag-info.txt must contain key: 'Ocrd-Base-Version-Checksum'");
//        } else {
//            // I don't understand the intention and function of Ocrd-Base-Version-Checksum yet, so
//            // this is unfinished here:
//            String value = metadata.get("Ocrd-Base-Version-Checksum").get(0);
//        }

        if (!metadata.contains("Ocrd-Mets")) {
            if (!Files.exists(bag.getRootDir().resolve("data").resolve("mets.xml"))) {
                res.add("mets.xml not found and 'Ocrd-Mets' not provided in bag-info.txt");
            }
        } else {
            String value = metadata.get("Ocrd-Mets").get(0);
            if (!Files.exists(bag.getRootDir().resolve("data").resolve(value))) {
                res.add("Ocrd-Mets is set, but specified file not existing");
            }
        }
        if (!res.isEmpty()) {
            throw new OcrdzipInvalidException(res);
        }
    }

    /**
     * - Zuerst die schlüssel aus der bag-info.txt lesen. Wenn die gesetzt sind, dann ab dafür
     * - wenn die schlüssel nicht gesetzt sind, dann
     *
     * @param archive
     * @param bagdir: bag location on disk
     * @param bagInfos:
     */
    private static void setSearchindexFilegrps(Archive archive, String bagdir,
            List<SimpleImmutableEntry<String, String>> bagInfos) {
        List<String> fileGrps = new ArrayList(0);
        try {
            fileGrps = Files.list(Paths.get(bagdir).resolve("data"))
                    .filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String imageFilegrp = null;
        String fulltextFilegrp = null;
        for (SimpleImmutableEntry<String, String> x : bagInfos) {
            if (StringUtils.isBlank(x.getValue())) {
                continue;
            }
            if (Constants.BAGINFO_KEY_IMAGE_FILEGRP.equals(x.getKey())) {
                imageFilegrp = x.getValue();
            } else if (Constants.BAGINFO_KEY_FULLTEXT_FILEGRP.equals(x.getKey())) {
                fulltextFilegrp = x.getValue();
            }
        }

        if (imageFilegrp != null && fileGrps.contains(imageFilegrp)) {
            archive.setImageFileGrp(imageFilegrp);
        } else {
            if (fileGrps.contains(Constants.DEFAULT_IMAGE_FILEGRP)) {
                archive.setImageFileGrp(Constants.DEFAULT_IMAGE_FILEGRP);
            } else {
                /* TODO: this situation is unexpected. think about moving the whole function to
                 * validation phase and deny validation if image-file-grp can not be determined */
            }
        }

        if (fulltextFilegrp != null && fileGrps.contains(imageFilegrp)) {
            archive.setFulltextFileGrp(imageFilegrp);
        } else {
            for (String canditate: Constants.FULLTEXT_FILEGRPS_CANDITATES) {
                if (fileGrps.contains(canditate)) {
                    archive.setFulltextFileGrp(canditate);
                    break;
                }
            }
        }
        if (archive.getFulltextFileGrp() == null) {
            /* TODO: this situation is unexpected. think about moving the whole function to
             * validation phase and deny validation if fulltext-file-grp can not be determined */
        }

    }

}
