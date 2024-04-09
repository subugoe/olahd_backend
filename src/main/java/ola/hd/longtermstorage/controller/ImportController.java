package ola.hd.longtermstorage.controller;

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
import ola.hd.longtermstorage.component.ExecutorWrapper;
import ola.hd.longtermstorage.controller.importarchive.BagImport;
import ola.hd.longtermstorage.controller.importarchive.BagImportParams;
import ola.hd.longtermstorage.controller.importarchive.FormParams;
import ola.hd.longtermstorage.controller.importarchive.ImportUtils;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.domain.TrackingInfo;
import ola.hd.longtermstorage.domain.TrackingStatus;
import ola.hd.longtermstorage.repository.mongo.TrackingRepository;
import ola.hd.longtermstorage.service.PidService;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final PidService pidService;

    private final ExecutorWrapper executor;

    private AutowireCapableBeanFactory beanFactory;

    @Value("${ola.hd.upload.dir}")
    private String uploadDir;

    @Value("${webnotifier.url}")
    private String webnotifierUrl;

    @Autowired
    public ImportController( TrackingRepository trackingRepository, PidService pidService, ExecutorWrapper executor,
        AutowireCapableBeanFactory beanFactory
    ) {
        this.trackingRepository = trackingRepository;
        this.pidService = pidService;
        this.executor = executor;
        this.beanFactory = beanFactory;
    }

    @ApiOperation(
        value = "Import a ZIP file into a system. It may be an independent ZIP, or a new version of another ZIP. In the second case, a PID of the previous ZIP must be provided.",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        authorizations = { @Authorization(value = "basicAuth"), @Authorization(value = "bearer")}
    )
    @ApiResponses(value = {
        @ApiResponse(
            code = 202, message = "The ZIP has a valid BagIt structure. The system is saving it to the archive.",
            response = ResponseMessage.class,
            responseHeaders = { @ResponseHeader(name = "Location", description = "The PID of the ZIP.", response = String.class) }
        ),
        @ApiResponse(code = 400, message = "The ZIP has an invalid BagIt structure.", response = ResponseMessage.class),
        @ApiResponse(code = 401, message = "Invalid credentials.", response = ResponseMessage.class),
        @ApiResponse(code = 415, message = "The request is not a multipart request.", response = ResponseMessage.class)
    })
    @ApiImplicitParams(value = {
            @ApiImplicitParam(dataType = "__file", name = "file", value = "The file to be imported", required = true, paramType = "form"),
            @ApiImplicitParam(dataType = "string", name = "prev", value = "The PID of the previous version", paramType = "form"),
            @ApiImplicitParam(dataType = "string", name = "fulltextfilegrp", value = "Name of filegroup containing the fulltexts", paramType = "form"),
            @ApiImplicitParam(dataType = "string", name = "imagefilegrp", value = "Name of filegroup containing the images", paramType = "form"),
            @ApiImplicitParam(dataType = "string", name = "fulltextftype", value = "Type of fulltexts, e.g. PAGEXML_1", paramType = "form"),
            @ApiImplicitParam(dataType = "boolean", name = "isgt", value = "Set to true to flag the data as Ground Truth, used for search filtering", paramType = "form"),
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
