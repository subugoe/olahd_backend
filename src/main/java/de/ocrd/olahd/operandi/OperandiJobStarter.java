package de.ocrd.olahd.operandi;

import static de.ocrd.olahd.utils.Utils.logDebug;

import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.OperandiJobInfo;
import de.ocrd.olahd.domain.OperandiJobStatus;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.repository.mongo.OperandiJobRepository;
import de.ocrd.olahd.service.ArchiveManagerService;
import de.ocrd.olahd.utils.Utils;
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

        String workspaceId = null;
        try {
            Response res = archiveManagerService.export(pid, isOnline ? "quick" : "full", false);
            workspaceId = operandiService.uploadWorkspace(res.body().byteStream());
            logDebug("runOperandiWorkflow - Uploaded workspace to operandi. Id: %s", workspaceId);
            jobinfo.setWorkspaceId(workspaceId);
            jobinfo = operandiJobRepository.save(jobinfo);
        } catch (Exception e) {
            // TODO: improve Error handling: if workspace upload fails set the job-status to failed or something else
            Utils.logError(
                String.format(
                    "Error while uploading workspace to operandi. Pid: %s. Job-Info-Id: %s", pid, jobinfo.getId()
                ), e
            );
            return;
        }

        try {
            String jobId = operandiService.runWorkflow(jobinfo.getWorkflowId(), workspaceId, jobinfo.getInputFileGroup());
            logDebug("runOperandiWorkflow - started operandi-workflow with id: %s", jobId);
            jobinfo.setOperandiJobId(jobId);
            jobinfo.setStatus(OperandiJobStatus.RUNNING);
            jobinfo = operandiJobRepository.save(jobinfo);
            logDebug("runOperandiWorkflow - Saved Operandi job in mongodb: %s", jobinfo.getId());
        } catch(Exception e) {
            // TODO: improve error handling: set job-status. Remove the workspace again from operandi
            Utils.logError(
                String.format(
                    "Error starting operandi workflow. Pid: %s. Workspace-id: %s. Job-Info-Id: %s", pid, workspaceId,
                    jobinfo.getId()
                ), e
            );
            return;
        }
    }
}
