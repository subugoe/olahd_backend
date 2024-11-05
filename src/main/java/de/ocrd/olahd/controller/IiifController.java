package de.ocrd.olahd.controller;

import de.ocrd.olahd.domain.ResponseMessage;
import de.ocrd.olahd.msg.ErrMsg;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.service.ArchiveManagerService;
import de.ocrd.olahd.service.S3Service;
import de.ocrd.olahd.utils.MetsWebConverter;
import de.ocrd.olahd.utils.Utils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Api(description = "This endpoint is used to provide IIIF-Manifests and related files for the TIFY viewer")
@RestController
public class IiifController {

    @Autowired
    private ArchiveRepository archiveRepository;

    @Autowired
    private ArchiveManagerService archiveManagerService;

    @Autowired
    private S3Service s3;

    /**
     * Download a IIIF Manifest through a PID
     *
     * Check with the mongdb if the PID has an existing archive. Then try to load the file from the s3. Set the
     * placeholder and return it. Check for errors if unexpectedly there is no IIIF-Manifest available for the PID.
     *
     * @param id PID
     * @return IIIF-Manifest for the PID
     * @throws IOException
     */
    @ApiOperation(value = "Download a IIIF-Manifest")
    @ApiResponses({
        @ApiResponse(code = 200, message = "IIIF-Manifest for specified identifier was found.", response = byte[].class),
        @ApiResponse(code = 404, message = "An archive with the specified identifier is not available.", response = ResponseMessage.class),
        @ApiResponse(code = 422, message = "Parameter PID is empty", response = ResponseMessage.class),
        @ApiResponse(code = 500, message = "The creation of af IIIF-Manifest for this PID failed", response = ResponseMessage.class)
    })
    @GetMapping(value = "/iiif/manifest", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<String> downloadManifest(
        HttpServletRequest request,
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam
        String id
    ) throws IOException {
        if (id.isBlank()) {
            throw new HttpClientErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_ID_IS_EMPTY
            );
        }

        if (archiveRepository.findByPid(id) == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "No archive available for this PID");
        }

        String manifest;
        try {
            manifest = s3.getManifest(id);
        } catch (NoSuchKeyException e) {
            Utils.logError(String.format("Archive (%s) without IIIF Manifest", id), e);
            throw new HttpClientErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrMsg.IIIF_MANIFEST_NOT_FOUND
            );
        }
        String baseUrl = Utils.getBaseUrl(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/json"))
            .body(replaceHostBaseUrl(manifest, baseUrl));
    }

    private static String replaceHostBaseUrl(String text, String baseUrl) {
        return text.replaceAll("[{]{2}\\s*HOST_BASE_URL\\s*[}]{2}", baseUrl);
    }

    /**
     * Download image
     *
     * Endpoint to provide an image for the iiif-manifest. Tiff images cannot be displayed with TIFY (most browsers do
     * not display tifs also). Therefore they are converted to jpg before sending them. Other image types (jpg, png)
     * are returned without converting
     *
     * {@linkplain #fullExportRequest(String, Principal)}.
     *
     * @param id   PID or PPA
     * @param path of file relative to the data-folder
     * @return file from archive's
     * @throws IOException
     */
    @ApiOperation(value = "Get an image for the iiif manifest in a web format")
    @ApiResponses({
        @ApiResponse(code = 200, message = "Image successfully send", response = byte[].class),
        @ApiResponse(code = 404, message = "An archive with the specified identifier is not available.", response = ResponseMessage.class),
        @ApiResponse(code = 404, message = "A file with the specified path is not available.", response = ResponseMessage.class) })
    @GetMapping(value = "/iiif/image", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<StreamingResponseBody> getImage(
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam
        String id,
        @ApiParam(value = "Path to image.", required = true) @RequestParam
        String path
    ) throws IOException {
        if (id.isBlank()) {
            throw new HttpClientErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_ID_IS_EMPTY
            );
        } else if (path.isBlank()) {
            throw new HttpClientErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_PATH_IS_EMPTY
            );
        }

        Response res;
        try {
            res = archiveManagerService.exportFile(id, Paths.get("data", path).toString());
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                String msg = e.getMessage().contains(ErrMsg.ARCHIVE_NOT_FOUND) ? ErrMsg.ID_NOT_FOUND
                    : ErrMsg.FILE_NOT_FOUND + ": " + path;
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND, msg);
            }
            throw e;
        }

        MediaType mediaType = MediaType.parseMediaType(res.headers().get(HttpHeaders.CONTENT_TYPE));
        // Convert to jpg and return

            StreamingResponseBody stream = outputStream -> {
                try {
                    InputStream imageInStream = res.body().byteStream();
                    if (mediaType.includes(MediaType.parseMediaType("image/tiff"))) {
                        MetsWebConverter.convertTifToJpg(imageInStream, outputStream);
                    } else {
                        outputStream.write(imageInStream.readAllBytes());
                    }
                    imageInStream.close();
                } catch (Exception e) {
                    Utils.logError(ErrMsg.METS_CONVERT_ERROR, e);
                    throw new HttpClientErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR, ErrMsg.TIFF_CONVERT_ERROR
                    );
                }
            };
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/jpeg")).body(stream);

    }
}
