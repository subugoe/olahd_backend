package ola.hd.longtermstorage.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import okhttp3.Headers;
import okhttp3.Response;
import ola.hd.longtermstorage.domain.ArchiveStatus;
import ola.hd.longtermstorage.domain.DownloadRequest;
import ola.hd.longtermstorage.domain.ExportRequest;
import ola.hd.longtermstorage.domain.HttpFile;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.msg.ErrMsg;
import ola.hd.longtermstorage.repository.mongo.ExportRequestRepository;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import ola.hd.longtermstorage.utils.MetsWebConverter;
import ola.hd.longtermstorage.utils.Utils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import springfox.documentation.annotations.ApiIgnore;

@Api(description = "This endpoint is used to export data from the system.")
@RestController
public class ExportController {

    private final ArchiveManagerService archiveManagerService;

    private final ExportRequestRepository exportRequestRepository;

    public ExportController(
        ArchiveManagerService archiveManagerService,
        ExportRequestRepository exportRequestRepository
    ) {
        this.archiveManagerService = archiveManagerService;
        this.exportRequestRepository = exportRequestRepository;
    }

    @ApiOperation(value = "Quickly export a ZIP file via PID. This ZIP file only contains files stored on hard disks.")
    @ApiResponses({
        @ApiResponse(code = 200, message = "An archive with the specified identifier was found.", response = byte[].class),
        @ApiResponse(code = 404, message = "An archive with the specified identifier was not found.", response = ResponseMessage.class) })
    @GetMapping(value = "/export", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE,
        MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<StreamingResponseBody> export(
        @ApiParam(value = "The ID of the work.", required = true) @RequestParam
        String id,
        @ApiParam(value = "Is this an internal ID or not (PID, PPN).", required = true) @RequestParam(defaultValue = "false")
        boolean internalId
    ) {
        return exportData(id, "quick", internalId);
    }

    /**
     * Move data from tape to disk to make it fully available
     *
     * Move data from tape to disk is an expensive operation when real tape storage is used. So this
     * operation has to always be authenticated. Maybe it is necessary to limit the access to only
     * special users
     *
     * @param id:        PID of the archive to move
     * @param principal: information of user who calld this function
     * @return
     */
    @ApiOperation(value = "Send a request to export data on tapes.", authorizations = {
        @Authorization(value = "basicAuth"), @Authorization(value = "bearer") })
    @ApiResponses({
        @ApiResponse(code = 200, message = "The archive is already on the hard drive.", response = byte[].class),
        @ApiResponse(code = 202, message = "Request accepted. Data is being transfer from tape to hard drive.", response = byte[].class),
        @ApiResponse(code = 404, message = "An archive with the specified identifier was not found.", response = ResponseMessage.class) })
    @GetMapping(value = "/export-request", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> fullExportRequest(
        @ApiParam(value = "The PID or the PPN of the work.", required = true) @RequestParam
        String id,
        @ApiIgnore
        Principal principal
    ) throws IOException {

        archiveManagerService.moveFromTapeToDisk(id);

        // Save the request info to the database
        ExportRequest exportRequest = new ExportRequest(
            principal.getName(), id, ArchiveStatus.PENDING
        );
        exportRequestRepository.save(exportRequest);

        return ResponseEntity.accepted()
            .body(new ResponseMessage(HttpStatus.ACCEPTED, "Your request is being processed."));
    }

    @ApiOperation(value = "Export the cold archive which was already moved to the hard drive.")
    @ApiResponses({
        @ApiResponse(code = 200, message = "An archive with the specified identifier was found.", response = byte[].class),
        @ApiResponse(code = 404, message = "An archive with the specified identifier was not found.", response = ResponseMessage.class),
        @ApiResponse(code = 409, message = "The archive is still on tape. A full export request must be made first.", response = ResponseMessage.class) })
    @GetMapping(value = "/export-full", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE,
        MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<StreamingResponseBody> fullExport(
        @ApiParam(value = "The PID or the PPN of the work.", required = true) @RequestParam
        String id,
        @ApiParam(value = "Is this an internal ID or not (PID, PPN).", required = true) @RequestParam(defaultValue = "false")
        boolean isInternal
    ) {
        return exportData(id, "full", isInternal);
    }

    @PostMapping(value = "/download", consumes = MediaType.APPLICATION_JSON_VALUE, produces = {
        MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<StreamingResponseBody> downloadFiles(
        @RequestBody
        DownloadRequest payload
    ) {

        // Set proper header
        String contentDisposition = "attachment;filename=download.zip";

        // Build the response stream
        StreamingResponseBody stream = outputStream -> {
            archiveManagerService.downloadFiles(
                payload.getArchiveId(), payload.getFiles(), outputStream, payload.isInternalId()
            );
        };

        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition).body(stream);
    }

    @GetMapping(value = "/download-file", produces = { MediaType.APPLICATION_XML_VALUE,
        MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<Resource> downloadFile(
        @ApiParam(value = "PID or internal ID of the archive.", required = true) @RequestParam
        String id,
        @ApiParam(value = "Is this an internal ID (CDStar-ID) or not (PID, PPN).", required = true) @RequestParam(defaultValue = "false")
        boolean internalId,
        @ApiParam(value = "Path to the requested file", required = true) @RequestParam
        String path
    ) throws IOException {

        HttpFile httpFile = archiveManagerService.getFile(id, path, false, internalId);
        ByteArrayResource resource = new ByteArrayResource(httpFile.getContent());

        HttpHeaders headers = httpFile.getHeaders();

        // Inline content-disposition: render the file directly on the browser if possible
        String contentDisposition = "inline";

        // Get proper content-type, or use application/octet-stream by default.
        // Without a proper content-type, the browser cannot display the file correctly.
        String contentType = headers.getContentType() != null ? headers.getContentType().toString()
            : "application/octet-stream";

        // Set charset
        contentType += ";charset=utf-8";

        long contentLength = headers.getContentLength();

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_LENGTH, contentLength + "").body(resource);
    }

    /**
     * Export data using {@linkplain ArchiveManagerService}
     *
     * @param id         PID or PPN
     * @param type       "quick" or "full"
     * @param isInternal true: mongoDB-id. false: PID or PPN
     * @return
     */
    private ResponseEntity<StreamingResponseBody> exportData(
        String id, String type, boolean isInternal
    ) {
        // Set proper file name
        String contentDisposition = "attachment;filename=";
        String fileName = "quick-export.zip";

        if (type != null && type.equals("full")) {
            fileName = "full-export.zip";
        }
        contentDisposition += fileName;

        // Build the response stream
        StreamingResponseBody stream = outputStream -> {

            try (Response response = archiveManagerService.export(id, type, isInternal)) {
                if (response.body() != null) {
                    InputStream inputStream = response.body().byteStream();

                    int numberOfBytesToWrite;
                    byte[] data = new byte[1024];

                    while ((numberOfBytesToWrite = inputStream.read(data, 0, data.length)) != -1) {
                        outputStream.write(data, 0, numberOfBytesToWrite);
                    }
                }
            }
        };

        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition).body(stream);
    }

    /**
     * Export METS-file via PID
     *
     * Expects the METS-file to be always stored in online-profile (Hot Storage)
     *
     * @param id PID or PPA
     * @return archive's METS-file
     * @throws IOException
     */
    @ApiOperation(value = "Quickly export the METS-file via PID")
    @ApiResponses({
        @ApiResponse(code = 200, message = "METS-File for specified identifier was found.", response = byte[].class),
        @ApiResponse(code = 422, message = "An archive with the specified identifier is not available.", response = ResponseMessage.class)
    })
    @GetMapping(value = "/export/mets", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<InputStreamResource> exportMetsfile(
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam
        String id
    ) throws IOException {
        if (id.isBlank()) {
            throw new HttpClientErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_ID_IS_EMPTY
            );
        }

        final String metsPath;
        Map<String, String> bagInfoMap;
        try {
            bagInfoMap = archiveManagerService.getBagInfoTxt(id);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                throw new HttpClientErrorException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.ID_NOT_FOUND
                );
            }
            throw e;
        }
        if (bagInfoMap.containsKey("Ocrd-Mets")) {
            metsPath = bagInfoMap.get("Ocrd-Mets");
        } else {
            metsPath = "data/mets.xml";
        }

        Response res;
        try {
            res = archiveManagerService.exportFile(id, metsPath);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                throw new HttpClientErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, ErrMsg.METS_NOT_FOUND
                );
            }
            throw e;
        }
        Headers headers = res.headers();

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(headers.get(HttpHeaders.CONTENT_TYPE)))
            .header(HttpHeaders.CONTENT_LENGTH, headers.get(HttpHeaders.CONTENT_LENGTH))
            .body(new InputStreamResource(res.body().byteStream()));
    }

    /**
     * Export METS-file via PID and rewrite the Files (FLocat) so that all files are accessible
     * through the web
     *
     * The links of the FLocat-Elements are converted to URLS where the corresponding files are
     * available for download
     *
     * @param id PID
     * @return archive's METS-file
     * @throws IOException
     */
    @ApiOperation(value = "Export a METS-file via PID with all files referenced web-accessible")
    @ApiResponses({
        @ApiResponse(code = 200, message = "METS-File for specified identifier was found.", response = byte[].class),
        @ApiResponse(code = 422, message = "An archive with the specified identifier is not available.", response = ResponseMessage.class)
    })
    @GetMapping(value = "/export/mets-web", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<StreamingResponseBody> exportMetsfileUrlpaths(
        HttpServletRequest request,
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam
        String id
    ) throws IOException {
        if (id.isBlank()) {
            throw new HttpClientErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_ID_IS_EMPTY
            );
        }

        final String metsPath;
        Map<String, String> bagInfoMap;
        try {
            bagInfoMap = archiveManagerService.getBagInfoTxt(id);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                throw new HttpClientErrorException(
                    HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.ID_NOT_FOUND
                );
            }
            throw e;
        }
        if (bagInfoMap.containsKey("Ocrd-Mets")) {
            metsPath = bagInfoMap.get("Ocrd-Mets");
        } else {
            metsPath = "data/mets.xml";
        }

        Response res;
        try {
            res = archiveManagerService.exportFile(id, metsPath);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                throw new HttpClientErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, ErrMsg.METS_NOT_FOUND
                );
            }
            throw e;
        }

        StreamingResponseBody stream = outputStream -> {
            try {
                InputStream metsInStream = res.body().byteStream();
                String host = Utils.readHost(request);
                MetsWebConverter.convertMets(id, host, metsInStream, outputStream);
            } catch (Exception e) {
                Utils.logError(ErrMsg.METS_CONVERT_ERROR, e);
                throw new HttpClientErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR, ErrMsg.METS_CONVERT_ERROR
                );
            }
        };

        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/xml")).body(stream);
    }

    /**
     * Export File from OCRD-ZIP data directory via PID
     *
     * The path must be relative to data-directory. The file must be available in the online-profile
     * (hot storage), it is not working if the file is only present in cold storage, even if it has
     * been processed with the 'export-request' operation.
     * {@linkplain #fullExportRequest(String, Principal)}.
     *
     * @param id   PID or PPA
     * @param path of file relative to the data-folder
     * @return file from archive's
     * @throws IOException
     */
    @ApiOperation(value = "Export a file via PID and path")
    @ApiResponses({ @ApiResponse(code = 200, message = "File was found.", response = byte[].class),
        @ApiResponse(code = 422, message = "An archive with the specified identifier is not available.", response = ResponseMessage.class),
        @ApiResponse(code = 422, message = "A file the specified path is not available.", response = ResponseMessage.class) })
    @GetMapping(value = "/export/file", produces = { MediaType.APPLICATION_OCTET_STREAM_VALUE })
    public ResponseEntity<InputStreamResource> exportFile(
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam
        String id,
        @ApiParam(value = "Path to file.", required = true) @RequestParam
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
                throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
            }
            throw e;
        }

        Headers headers = res.headers();

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(headers.get(HttpHeaders.CONTENT_TYPE)))
            .header(HttpHeaders.CONTENT_LENGTH, headers.get(HttpHeaders.CONTENT_LENGTH))
            .body(new InputStreamResource(res.body().byteStream()));
    }

}
