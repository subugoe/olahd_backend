package de.ocrd.olahd.controller;

import de.ocrd.olahd.Constants;
import de.ocrd.olahd.component.ExecutorWrapper;
import de.ocrd.olahd.controller.importarchive.BagImport;
import de.ocrd.olahd.controller.importarchive.BagImportParams;
import de.ocrd.olahd.controller.importarchive.FormParams;
import de.ocrd.olahd.controller.importarchive.ImportUtils;
import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.ResponseMessage;
import de.ocrd.olahd.domain.TrackingInfo;
import de.ocrd.olahd.domain.TrackingStatus;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.repository.mongo.TrackingRepository;
import de.ocrd.olahd.service.PidService;
import de.ocrd.olahd.utils.Utils;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.AbstractMap;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import springfox.documentation.annotations.ApiIgnore;

@Api(description = "This endpoint is used to import a ZIP file into the system")
@RestController
public class ImportController {

    private static final Logger logger = LoggerFactory.getLogger(ImportController.class);

    private final TrackingRepository trackingRepository;

    private final ArchiveRepository archiveRepository;

    private final PidService pidService;

    private final ExecutorWrapper executor;

    private AutowireCapableBeanFactory beanFactory;

    @Value("${ola.hd.upload.dir}")
    private String uploadDir;

    @Value("${webnotifier.url}")
    private String webnotifierUrl;

    public ImportController(
        TrackingRepository trackingRepository, ArchiveRepository archiveRepository,
        PidService pidService, ExecutorWrapper executor,
        AutowireCapableBeanFactory beanFactory
    ) {
        this.trackingRepository = trackingRepository;
        this.archiveRepository = archiveRepository;
        this.pidService = pidService;
        this.executor = executor;
        this.beanFactory = beanFactory;
    }

    @ApiOperation(
        value = "Import a ZIP file into the system. It may be an independent ZIP, or a new version of another ZIP. In the second case, a PID of the previous ZIP must be provided.",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        authorizations = { @Authorization(value = "basicAuth"), @Authorization(value = "bearer")}
    )
    @ApiResponses(value = {
        @ApiResponse(
            code = 202, message = "The OCRD-ZIP has a valid BagIt structure. The system is saving it to the archive.",
            response = ResponseMessage.class,
            responseHeaders = { @ResponseHeader(name = "Location", description = "The PID of the ZIP.", response = String.class) }
        ),
        @ApiResponse(code = 400, message = "The OCRD-ZIP is invalid.", response = ResponseMessage.class),
        @ApiResponse(code = 401, message = "Invalid credentials.", response = ResponseMessage.class),
        @ApiResponse(code = 409, message = "The same archive (checked through payload checksum) with the same ocrd-identifier already exists.", response = ResponseMessage.class),
        @ApiResponse(code = 415, message = "The request is not a multipart request.", response = ResponseMessage.class)
    })
    @ApiImplicitParams(value = {
            @ApiImplicitParam(dataType = "__file", name = "file", value = "The file to be imported", required = true, paramType = "form"),
            @ApiImplicitParam(dataType = "string", name = "prev", value = "The PID of the previous version", paramType = "form"),
    })
    @ResponseStatus(value = HttpStatus.ACCEPTED)
    @PostMapping(value = "/bag", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importArchive(HttpServletRequest request, @ApiIgnore Principal principal)
        throws IOException, FileUploadException {
        // **every** Import-Request creates an info in tracking-database-table
        TrackingInfo info = new TrackingInfo(principal.getName(), TrackingStatus.PROCESSING, "Processing...", null);

        if (!ServletFileUpload.isMultipartContent(request)) {
            ImportUtils.throwClientException("The request must be multipart request.", info,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, trackingRepository
            );
        }
        Path tempDir = Paths.get(uploadDir, UUID.randomUUID().toString());
        FormParams formParams = ImportUtils.readFormParams(request, info, tempDir, trackingRepository);
        File targetFile = formParams.getFile();  // The uploaded file (ZIP)
        Path destination = tempDir.resolve(FilenameUtils.getBaseName(targetFile.getName()) + "_extracted");

        List<AbstractMap.SimpleImmutableEntry<String, String>> bagInfos = ImportUtils.extractAndVerifyOcrdzip(
                targetFile.toPath(), destination, tempDir, info, formParams, trackingRepository
        );

        // Set previous version in two cases: 1. If Ocrdzip with same OcrdIdentifier exist
        // 2. if prev-pid is provided in the bag-info.txt
        if (StringUtils.isBlank(formParams.getPrev())) {
            String checksumPayloadmanifest = ImportUtils.generatePayloadmanifestChecksum(destination);
            String ocrdIdentifier = ImportUtils.readOcrdIdentifier(bagInfos);
            Archive prevArchive = archiveRepository.findTopByOcrdIdentifierOrderByCreatedAtDesc(ocrdIdentifier);
            if (prevArchive != null) {
                if (checksumPayloadmanifest.equals(prevArchive.getChecksumPayloadmanifest())) {
                    // Abort if archive with same OcrdIdentifier and same payload already exists
                    ImportUtils.throwClientException(
                        String.format(
                            "Newest archive of OcrdIdentifier '%s' has the same payload(-checksum).",
                            ocrdIdentifier
                        ), info,
                        HttpStatus.CONFLICT, trackingRepository
                    );
                } else {
                    formParams.setPrev(prevArchive.getPid());
                }
            } else {
                String prevPid = ImportUtils.readBagInfoValue(bagInfos, Constants.BAGINFO_KEY_PREV_PID);
                if (!Utils.isNullValue(prevPid)) {
                    formParams.setPrev(prevPid);
                }
            }
        }

        // Create a PID with meta-data from bag-info.txt
        String pid = Failsafe.with(ImportUtils.RETRY_POLICY).get(() -> pidService.createPid(bagInfos));
        if (StringUtils.isBlank(pid)) {
            ImportUtils.throwClientException(
                "No PID received", info, HttpStatus.INTERNAL_SERVER_ERROR, trackingRepository
            );
        } else {
            info.setPid(pid);
        }

        // **here the OCRD-ZIP is scheduled to be saved** to the external archive
        executor.submit(
            BagImport.create(
                beanFactory,
                new BagImportParams(destination, pid, formParams, bagInfos, info, tempDir, webnotifierUrl)
            )
        );

        // Inform the user that the import is done in the background.
        trackingRepository.save(info);
        ResponseMessage responseMessage = new ResponseMessage(HttpStatus.ACCEPTED, "Your data is being processed.");
        responseMessage.setPid(pid);

        return ResponseEntity.accepted().body(responseMessage);
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
}
