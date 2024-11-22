package de.ocrd.olahd.controller.importarchive;

import com.google.common.hash.Hashing;
import de.ocrd.olahd.Constants;
import de.ocrd.olahd.domain.TrackingInfo;
import de.ocrd.olahd.domain.TrackingStatus;
import de.ocrd.olahd.exceptions.BagitChecksumException;
import de.ocrd.olahd.exceptions.MetsInvalidException;
import de.ocrd.olahd.exceptions.MetsSchemaException;
import de.ocrd.olahd.exceptions.OcrdzipInvalidException;
import de.ocrd.olahd.repository.mongo.TrackingRepository;
import de.ocrd.olahd.utils.BagitManifestValidation;
import de.ocrd.olahd.utils.Utils;
import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.exceptions.CorruptChecksumException;
import gov.loc.repository.bagit.reader.BagReader;
import gov.loc.repository.bagit.verify.BagVerifier;
import gov.loc.repository.bagit.verify.MandatoryVerifier;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.http.HttpServletRequest;
import net.jodah.failsafe.RetryPolicy;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

public class ImportUtils {

    /** Retry policies when a call to another service is failed */
    public static RetryPolicy<Object> RETRY_POLICY = new RetryPolicy<>().withDelay(Duration.ofSeconds(10)).withMaxRetries(3);

    private ImportUtils() {};

    private static final Logger logger = LoggerFactory.getLogger(ImportUtils.class);

    /**
     * Read ocrd-identifier and payload oxum from bag-infos
     *
     * @param bagInfos
     * @return
     */
    static String[] readOcrdIdentifierAndPayloadOxum(List<SimpleImmutableEntry<String, String>> bagInfos) {
        String ocrdIdentifier = null;
        String payloadOxum = null;

        for (SimpleImmutableEntry<String, String> x : bagInfos) {
            if (StringUtils.isBlank(x.getValue())) {
                continue;
            }
            if (Constants.BAGINFO_KEY_OCRD_IDENTIFIER.equals(x.getKey())) {
                ocrdIdentifier = x.getValue();
            } else if (Constants.BAGINFO_KEY_PAYLOAD_OXUM.equals(x.getKey())) {
                payloadOxum = x.getValue();
            }
            if (ocrdIdentifier != null && payloadOxum != null) {
                break;
            }
        }
        return new String[] { ocrdIdentifier, payloadOxum };
    }

    /**
     * Read ocrd-identifier from bag-infos
     *
     * @param bagInfos
     * @return
     */
    public static String readOcrdIdentifier(List<SimpleImmutableEntry<String, String>> bagInfos) {
        return readBagInfoValue(bagInfos, Constants.BAGINFO_KEY_OCRD_IDENTIFIER);
    }

    /**
     * Read value from bag-infos
     *
     * @param bagInfos
     * @param key - key to read
     * @return
     */
    public static String readBagInfoValue(List<SimpleImmutableEntry<String, String>> bagInfos, String key) {
        if (StringUtils.isBlank(key)) {
            return "";
        }
        for (SimpleImmutableEntry<String, String> x : bagInfos) {
            if (key.equals(x.getKey())) {
                if (StringUtils.isBlank(x.getValue())) {
                    return "";
                } else {
                    return x.getValue();
                }
            }
        }
        return "";
    }

    /**
     * Create sha512-checksum for file "manifest-sha512.txt"
     *
     * @param bagDir Path to unpacked ocrdzip
     * @return
     */
    public static String generatePayloadmanifestChecksum(Path bagDir) {
        try {
            return com.google.common.io.Files
                .asByteSource(bagDir.resolve(Constants.PAYLOAD_MANIFEST_NAME).toFile()).hash(Hashing.sha512())
                .toString();
        } catch (IOException e) {
            throw new RuntimeException("Error generating sha512 checksum of payload manifest", e);
        }
    }

    /**
     * Extract bagit, read metadata and verify that the ZIP-file is a valid bagit.
     *
     * Additionally validate that bag is valid according to ocrd-zip: https://ocr-d.de/en/spec/ocrd_zip.
     *
     * @param targetFile Location of the ZIP-File
     * @param destination Where to extract the file
     * @param tempDir Temporary directory to store the ZIP-file in. Needed in case of Exception to
     *        clean up
     * @param info Needed to set error tracking info in case bag is not valid
     * @return
     * @throws IOException
     */
    public static List<AbstractMap.SimpleImmutableEntry<String, String>> extractAndVerifyOcrdzip(
        Path targetFile, Path destination, Path tempDir, TrackingInfo info, FormParams params,
        TrackingRepository trackingRepository
    ) throws IOException {
        Bag bag;
        // Default executor service used crashes with about more than 20.00 files.
        ExecutorService exeService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try (BagVerifier verifier = new BagVerifier(exeService); ZipFile zipFile = new ZipFile(targetFile.toFile())) {
            // Extract the zip file
            zipFile.extractAll(destination.toString());
            BagReader reader = new BagReader();

            // Create a bag from an existing directory
            bag = reader.read(destination);

            if (BagVerifier.canQuickVerify(bag)) {
                BagVerifier.quicklyVerify(bag);
                MandatoryVerifier.checkBagitFileExists(bag.getRootDir(), bag.getVersion());
                MandatoryVerifier.checkPayloadDirectoryExists(bag);
                MandatoryVerifier.checkIfAtLeastOnePayloadManifestsExist(bag.getRootDir(), bag.getVersion());
            }

            // Validate payload and tag manifest
            new BagitManifestValidation(destination).validate(true);

            Validation.validateOcrdzip(bag, destination, params);
            Validation.validateMetsfileSchema(bag);
        } catch (Exception ex) {
            // Clean up the temp
            FileSystemUtils.deleteRecursively(tempDir.toFile());

            String message;
            if (ex instanceof BagitChecksumException) {
                message = "Not a valid Bagit: " + StringUtils.join(((BagitChecksumException)ex).getErrors(), ", ");
            } else if (ex instanceof OcrdzipInvalidException) {
                message = "Not a valid Ocrd-Zip: " + StringUtils.join(((OcrdzipInvalidException)ex).getErrors(), ", ");
            } else if (ex instanceof MetsInvalidException) {
                message = "Invalid METS: " + ((MetsInvalidException)ex).getMetsErrorMessage();
            } else if (ex instanceof CorruptChecksumException && ex.getMessage() != null) {
                // Try to give more detailed error description
                message = "Invalid file input. The uploaded file must be a ZIP file with BagIt structure.";
                message += " Details: " + ex.getMessage() + " There may be further bagit-validation-errors";
            } else if (ex instanceof MetsSchemaException) {
                throw new HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Cannot validate METS because schema cannot currently be created"
                );
            } else {
                message = "Unexpected error validating Ocrdzip";
            }
            // Save to the tracking database
            info.setStatus(TrackingStatus.FAILED);
            info.setMessage(message);
            trackingRepository.save(info);

            // Throw a friendly message to the client
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, message);
        }
        return bag.getMetadata().getAll();
    }

    /**
     * Read request parameters and save the uploaded OCRD-ZIP to a temporary file
     *
     * The import request must contain one ZIP and can (optionally) contain a PID of a previously
     * stored ZIP. This Method reads the ZIP-file and saves it to the temporary-directory.
     * Moreover it reads and returns the PID of a previous work if it is provided. If there is
     * not exactly one ZIP-file provided in the request a HttpClientErrorException is thrown.
     * More form parameter have been added over time and all the parameters are read, validated and returned here.
     *
     * @param request   request is needed to get the parameters
     * @param principal user who initiated request. User name is needed for potential error messages
     * @param uploadDir   Temporary directory to store the ZIP-file in
     * @return
     * @throws IOException              forwarded from apache-commons
     * @throws FileUploadException      forwarded from apache-commons
     * @throws HttpClientErrorException if not exactly one ZIP-file is provided, or when io-error
     *                                  occurred while writing to temporary-directory
     */
    public static FormParams readFormParams(
        HttpServletRequest request, TrackingInfo info, Path tempDir, TrackingRepository trackingRepository
    ) throws FileUploadException, IOException {
        Utils.logDebug("Trying to read form params. Request: " + Utils.readRequestInfos(request));
        FormParams res = new FormParams();
        File targetFile = null;

        // 'fileCount' to make sure that there is only 1 file uploaded
        int fileCount = 0;
        FileItemIterator iterStream = new ServletFileUpload().getItemIterator(request);
        while (iterStream.hasNext()) {
            FileItemStream item = iterStream.next();

            // Is it a file?
            if (!item.isFormField()) {
                if (fileCount > 1) {
                    FileSystemUtils.deleteRecursively(tempDir.toFile());
                    throwClientException("Only 1 zip file is allowed.", info,
                        HttpStatus.BAD_REQUEST, trackingRepository
                    );
                } else {
                    fileCount += 1;
                }

                targetFile = tempDir.resolve(item.getName()).toFile();
                res.setFile(targetFile);
                try (InputStream uploadedStream = item.openStream();
                    OutputStream out = FileUtils.openOutputStream(targetFile)
                ) {
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
                        res.setPrev(Streams.asString(stream));
                    }
                }
            }
        }
        if (targetFile == null) {
            throwClientException("The request must contain 1 zip file.", info,
                    HttpStatus.BAD_REQUEST, trackingRepository);
        }

        // Not a ZIP file?
        Tika tika = new Tika();
        String mimeType = tika.detect(targetFile);
        if (!mimeType.equals("application/zip")) {
            // Clean up the temporary directory
            FileSystemUtils.deleteRecursively(tempDir.toFile());
            throwClientException("The file must be in the ZIP format", info,
                HttpStatus.BAD_REQUEST, trackingRepository
            );
        }

        return res;
    }


    /**
     * If an unrecoverable error occurs this method saves the error in the tracking-database and
     * throws a (Runtime)Exception
     *
     * @param msg reason of failure
     * @param info template for tracking-info
     * @param status http-status-code to return
     */
    public static void throwClientException(String msg, TrackingInfo info, HttpStatus status,
        TrackingRepository trackingRepository
    ) {
        info.setStatus(TrackingStatus.FAILED);
        info.setMessage(msg);
        trackingRepository.save(info);
        throw new HttpClientErrorException(status, msg);
    }
}
