package de.ocrd.olahd.controller;

import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.ArchiveResponse;
import de.ocrd.olahd.domain.TrackingInfo;
import de.ocrd.olahd.domain.TrackingResponse;
import de.ocrd.olahd.domain.TrackingStatus;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.repository.mongo.TrackingRepository;
import de.ocrd.olahd.utils.Utils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(description = "This endpoint is used to get information for administration purposes.")
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TrackingRepository trackingRepository;
    private final ArchiveRepository archiveRepository;

    public AdminController(TrackingRepository trackingRepository, ArchiveRepository archiveRepository) {
        this.trackingRepository = trackingRepository;
        this.archiveRepository = archiveRepository;
    }

    @ApiOperation(value = "Get information about the user's import processes.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Query success", response = TrackingInfo[].class)
    })
    @GetMapping(value = "/import-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TrackingResponse>> getImportData(String username, int page, int limit) {
        List<TrackingInfo> trackingInfos = trackingRepository.findByUsername(username, PageRequest.of(page, limit, Sort.Direction.DESC, "timestamp"));

        List<TrackingResponse> results = new ArrayList<>();

        TrackingResponse trackingResponse;
        for (TrackingInfo trackingInfo : trackingInfos) {

            // With each successful import
            if (trackingInfo.getStatus() == TrackingStatus.SUCCESS) {

                // Get more info (version, online/offline ID in CDSTAR...)
                Archive archive = archiveRepository.findByPid(trackingInfo.getPid());
                ArchiveResponse archiveResponse = new ArchiveResponse();
                if (archive == null) {
                    Utils.logWarn("Archive for tracking-info not found. Pid: " + trackingInfo.getPid());
                } else {
                    archiveResponse.setPid(archive.getPid());
                    archiveResponse.setOnlineId(archive.getOnlineId());
                    archiveResponse.setOfflineId(archive.getOfflineId());
                }
                trackingResponse = new TrackingResponse(trackingInfo, archiveResponse);
            } else {
                trackingResponse = new TrackingResponse(trackingInfo);
            }
            results.add(trackingResponse);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(results);
    }
}
