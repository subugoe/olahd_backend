package de.ocrd.olahd.operandi;

import static de.ocrd.olahd.utils.Utils.logDebug;

import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.OperandiJobInfo;
import de.ocrd.olahd.domain.OperandiJobStatus;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.repository.mongo.OperandiJobRepository;
import de.ocrd.olahd.service.ArchiveManagerService;
import de.ocrd.olahd.utils.Utils;
import java.io.IOException;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * Runnable to start operandi jobs
 *
 * This class uploads the workspace to operandi and starts the workflow-job
 */
public class OperandiJobStarter implements Runnable {

    @Autowired
    private OperandiJobRepository operandiJobRepository;

    @Autowired
    private ArchiveManagerService archiveManagerService;

    @Autowired
    private ArchiveRepository archiveRepository;

    @Autowired
    private OperandiService operandiService;

    private OperandiJobInfo jobinfo;

    /**
     * Create the bean with factory method instead of constructor to be able to inject the services
     *
     * @param factory
     * @return
     */
    public static OperandiJobStarter create(AutowireCapableBeanFactory factory, OperandiJobInfo jobinfo) {
        if (jobinfo == null) {
            throw new RuntimeException("jobinfo must not be null");
        }
        OperandiJobStarter res = new OperandiJobStarter();
        factory.autowireBean(res);
        res.jobinfo = jobinfo;

        return res;
    }

    @Override
    public void run() {
        jobinfo.setStatus(OperandiJobStatus.PREPARING);
        jobinfo = operandiJobRepository.save(jobinfo);

        String pid = this.jobinfo.getPid();
        Archive archive = archiveRepository.findByPid(pid);
        boolean isOnline = StringUtils.isNotBlank(archive.getOnlineId());
        if (!isOnline) {
            Boolean isOnDisk = null;
            try {
                isOnDisk = archiveManagerService.isArchiveOnDisk(pid);
            } catch(IOException e) {
                //pass
            }
            if (!Boolean.TRUE.equals(isOnDisk)) {
                this.handleError("Internal error. Archive not online and not on disk", null);
                return;
            }
        }

        String workspaceId = null;
        try {
            Response res = archiveManagerService.export(pid, isOnline ? "quick" : "full", false);
            if (!res.isSuccessful()) {
                String msg = res.body() != null ? res.body().toString() : "no body";
                throw new Exception(
                    String.format("Error exporting archive from operandi. Code: %d. Text: %s", res.code(), msg)
                );
            }

            workspaceId = operandiService.uploadWorkspace(res.body().byteStream());
            logDebug("runOperandiWorkflow - Uploaded workspace to operandi. Id: %s", workspaceId);
            jobinfo.setWorkspaceId(workspaceId);
            jobinfo = operandiJobRepository.save(jobinfo);
        } catch (Exception e) {
            this.handleError("Error while uploading workspace to operandi", workspaceId);
            return;
        }

        try {
            String jobId = operandiService
                .runWorkflow(jobinfo.getWorkflowId(), workspaceId, jobinfo.getInputFileGroup());
            logDebug("runOperandiWorkflow - started operandi-workflow with id: %s", jobId);
            jobinfo.setOperandiJobId(jobId);
            jobinfo.setStatus(OperandiJobStatus.RUNNING);
            jobinfo = operandiJobRepository.save(jobinfo);
            logDebug("runOperandiWorkflow - Saved Operandi job in mongodb: %s", jobinfo.getId());
        } catch(Exception e) {
            this.handleError("Error starting operandi workflow", workspaceId);
            return;
        }
    }

    private void handleError(String msg, String workspaceId) {
        String logmsg = String.format("%s. Pid: %s. Job-Info-Id: %s", msg, this.jobinfo.getPid(), this.jobinfo.getId());
        if (StringUtils.isNotBlank(workspaceId)) {
            logmsg = logmsg + ". Workspace-Id: " + workspaceId;
        }
        Utils.logError(logmsg, null);
        this.jobinfo.setStatus(OperandiJobStatus.PREPARING_FAILED);
        this.jobinfo = operandiJobRepository.save(this.jobinfo);
    }
}
