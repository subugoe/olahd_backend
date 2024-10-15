package ola.hd.longtermstorage.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.msg.ErrMsg;
import ola.hd.longtermstorage.repository.mongo.ArchiveRepository;
import ola.hd.longtermstorage.service.S3Service;
import ola.hd.longtermstorage.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@Api(description = "This endpoint is used to provide IIIF-Manifests and related files for the TIFY viewer")
@RestController
public class IiifController {

    @Autowired
    private ArchiveRepository archiveRepository;

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

        String manifest = s3.getManifest(id);
        String baseUrl = Utils.getBaseUrl(request);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/json"))
            .body(replaceHostBaseUrl(manifest, baseUrl));
    }

    private static String replaceHostBaseUrl(String text, String baseUrl) {
        return text.replaceAll("[{]{2}\\s*HOST_BASE_URL\\s*[}]{2}", baseUrl);
    }
}
