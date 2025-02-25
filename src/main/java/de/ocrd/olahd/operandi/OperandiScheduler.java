package de.ocrd.olahd.operandi;

import de.ocrd.olahd.domain.OperandiJobInfo;
import de.ocrd.olahd.domain.OperandiJobStatus;
import de.ocrd.olahd.repository.mongo.OperandiJobRepository;
import de.ocrd.olahd.utils.Utils;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Periodically run jobs to handle operandi-workflow-jobs
 *
 * This scheduler schedules tasks every few minutes to find and finalize (set infos, remove workspace) finished
 * operandi-workflow-jobs
 *
 * TODO: think about adding a task to find finished jobs with not deleted workspaces (these jobs have to be handled
 * manually and should only occur in case of errors)
 */
@Component
public class OperandiScheduler {

    // TODO: set to 5
    private final static int FIRST_RUN_AFTER_X_MIN = 1;
    // TODO: set to 30
    private final static int RUN_EVERY_X_MIN = 5;

    @Autowired
    private OperandiJobRepository operandiJobRepository;

    @Autowired
    private OperandiService operandiService;

    @Scheduled(initialDelay = FIRST_RUN_AFTER_X_MIN * 60 * 1000, fixedRate = RUN_EVERY_X_MIN * 60 * 1000)
    public void handleRunningOperandiJobs() {
        List<OperandiJobInfo> jobs = null;
        try {
            jobs = operandiJobRepository.findByStatusIn(List.of(OperandiJobStatus.RUNNING));
        } catch (Exception e) {
            Utils.logError("Error querying running Operandi Jobs from mongdb", e);
            return;
        }

        if (CollectionUtils.isEmpty(jobs)) {
            Utils.logDebug("No running Operandi Jobs");
            return;
        }

        for (OperandiJobInfo job : jobs) {
            this.handleOperandiJob(job);
        }
    }

    /**
     * Process a previously running operandi job
     * - query status and find finished (success or failed) jobs
     * - upload success-jobs to olahd
     * - delete to operandi uploaded workspaces after the workspace has finished
     *
     * @param job
     */
    private void handleOperandiJob(OperandiJobInfo job) {
        String status = null;
        try {
            status = operandiService.getJobStatus(job.getWorkflowId(), job.getOperandiJobId());
            if (StringUtils.isAllBlank(status)) {
                throw new Exception("Empty job status");
            }
        } catch (Exception e) {
            Utils.logError(
                String.format(
                    "handleOperandiJobs: Error querying operandi-job-status. job-info-id: %s. operandi-job-id: %s",
                    job.getWorkflowId(), job.getOperandiJobId()
                ), e
            );
            job.setStatus(OperandiJobStatus.UNKNOWN);
            return;
        }

        if (status.equalsIgnoreCase("success")) {
            job.setStatus(OperandiJobStatus.SUCCESS);
            try {
                String resultPid = operandiService.uploadToOlahd(job.getWorkspaceId());
                job.setPidResult(resultPid);
            } catch (Exception e) {
                Utils.logError("Error triggering olahd upload", e);
            }
            try {
                operandiService.deleteWorkspace(job.getWorkspaceId());
                job.setWorkspaceDeleted(true);
                Utils.logDebug("handleRunningOperandiJobs: Deleted workspace from Operandi: %s", job.getWorkspaceId());
            } catch (Exception e) {
                Utils.logError("Error deleting workspace from operandi (successful workflow job)", e);
            }
        } else if (status.equalsIgnoreCase("failed")) {
            job.setStatus(OperandiJobStatus.FAILED);
            try {
                operandiService.deleteWorkspace(job.getWorkspaceId());
                job.setWorkspaceDeleted(true);
                Utils.logDebug("handleRunningOperandiJobs: Deleted workspace from Operandi: %s", job.getWorkspaceId());
            } catch (Exception e) {
                Utils.logError("Error deleting workspace from operandi (failed workflow job)", e);
            }
        } else {
            job.setCheckCounter(job.getCheckCounter() + 1);
        }
        job = operandiJobRepository.save(job);
    }
}
