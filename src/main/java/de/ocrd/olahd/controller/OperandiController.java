package de.ocrd.olahd.controller;

import static de.ocrd.olahd.utils.Utils.logWarn;

import de.ocrd.olahd.component.ExecutorWrapper;
import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.OperandiJobInfo;
import de.ocrd.olahd.domain.ResponseMessage;
import de.ocrd.olahd.domain.RunOperandiWorkflowResponse;
import de.ocrd.olahd.domain.TrackingInfo;
import de.ocrd.olahd.msg.ErrMsg;
import de.ocrd.olahd.operandi.OperandiJobStarter;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.repository.mongo.OperandiJobRepository;
import de.ocrd.olahd.service.ArchiveManagerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import springfox.documentation.annotations.ApiIgnore;

@Api(description = "Execute Operandi Jobs")
@RestController
public class OperandiController {

    @Autowired
    private ArchiveRepository archiveRepository;

    @Autowired
    private OperandiJobRepository operandiJobRepository;

    @Autowired
    private ArchiveManagerService archiveManagerService;

    @Autowired
    private ExecutorWrapper executor;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    /** Operandi-Internal-Id of the workflow */
    private static final String WORKFLOW_ID = "olahd-workflow-1";

    /**
     * Download a IIIF Manifest through a PID
     *
     * Check with the mongodb if the PID has an existing archive. Then try to load the file from the s3. Set the
     * placeholder and return it. Check for errors if unexpectedly there is no IIIF-Manifest available for the PID.
     *
     * @param id PID
     * @return IIIF-Manifest for the PID
     * @throws IOException
     */
    @ApiOperation(value = "Run an operandi workflow")
    @ApiResponses({
        @ApiResponse(code = 200, message = "Operandi workflow started"),
        @ApiResponse(code = 500, message = "Internal error transfering a job to operandi", response = ResponseMessage.class)
    })
    @PostMapping(value = "/operandi/run-workflow", produces = { MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<RunOperandiWorkflowResponse> runOperandiWorkflow(
        HttpServletRequest request,
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam String id,
        @ApiIgnore Principal principal
    ) throws IOException {
        // Pid is provided
        if (id.isBlank()) {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_ID_IS_EMPTY);
        }

        // Only one running Job per user for now
        if (operandiJobRepository.hasRunningJob(principal.getName())) {
            throw new HttpClientErrorException(HttpStatus.CONFLICT, "This user already has a running job. Only one"
                + " running operandi job is permitted currently");
        }

        // There is an Archive for this PID and it is not on Tape only
        Archive archive = archiveRepository.findByPid(id);
        if (archive == null) {
            logWarn("runOperandiWorkflow - No archive found for PID: %s", id);
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "No archive available for this PID");
        }
        boolean isOnline = StringUtils.isNotBlank(archive.getOnlineId());
        boolean isOnDisk = archiveManagerService.isArchiveOnDisk(id);
        if (!isOnline && !isOnDisk) {
            logWarn("runOperandiWorkflow - Archive not on disk for pid PID: %s", id);
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "Archive not on disk, it needs to be"
                + " moved from tape to disk first");
        }

        // The usual input-file-group is available
        String inputFileGrp = null;
        List<String> filegrps = archiveManagerService.readFilegroups(id);
        if (filegrps.contains("DEFAULT")) {
            inputFileGrp = "DEFAULT";
        } else if (filegrps.contains("OCR-D-IMG")) {
            inputFileGrp = "OCR-D-IMG";
        } else {
            throw new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "Image FileGrp not found");
        }

        OperandiJobInfo job = OperandiJobInfo.createInitOperandiJobInfo(
            principal.getName(), id, inputFileGrp, WORKFLOW_ID
        );
        job = operandiJobRepository.save(job);

        executor.submit(
            OperandiJobStarter.create(
                beanFactory,
                job
            )
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/json"))
            .body(new RunOperandiWorkflowResponse(job.getId(), job.getPid(), "Operandi workflow started"));
    }

    @ApiOperation(value = "Get information about the user's operandi-workflow-jobs.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Query success", response = TrackingInfo[].class)
    })
    @GetMapping(value = "/operandi/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<OperandiJobInfo>> getOperandiJobs(
        @ApiParam(value = "Paginator: number of page", required = true) @RequestParam(defaultValue = "0")
        int page,
        @ApiParam(value = "Pagination results per page", required = true) @RequestParam(defaultValue = "10")
        int limit,
        @ApiIgnore
        Principal principal
    ) {
        System.out.println("page: " + page + ". limit: " + limit + ". username: " + principal.getName());

        List<OperandiJobInfo> joblist = operandiJobRepository
            .findByUsername(principal.getName(), PageRequest.of(page, limit, Sort.Direction.DESC, "created"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(joblist);
    }
}
