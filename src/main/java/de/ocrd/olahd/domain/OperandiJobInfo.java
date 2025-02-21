package de.ocrd.olahd.domain;

import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;


/**
 * Class to store information about operandi jobs in the mongodb
 *  */
@Document(collection = "operandi_job")
public class OperandiJobInfo {

    @Id
    private String id;

    /** Who performs the action */
    private String username;

    @CreatedDate
    private LocalDateTime created;

    @LastModifiedDate
    private LocalDateTime updated;

    /** Status of the job from operandi */
    private OperandiJobStatus status;

    /** The PID of the input workspace */
    private String pid;

    /** The PID of the result workspace */
    private String pidResult;

    /** Operandi-Id of the workflow-job */
    private String operandiJobId;

    /** Id of the (nextflow-)workflow */
    private String workflowId;

    /** Id of the operandi workspace */
    private String workspaceId;

    /** InputFileGroup to use */
    private String inputFileGroup;

    /** Counter to keep track about how often the job status was queried from operandi*/
    private Integer checkCounter = 0;

    private Boolean workspaceDeleted = false;

    private OperandiJobInfo() {
    }

    public static OperandiJobInfo createInitOperandiJobInfo(
        String username, String pid, String inputFileGroup, String workflowId
    ) {
        OperandiJobInfo res = new OperandiJobInfo();
        res.setUsername(username);
        res.setPid(pid);
        res.setInputFileGroup(inputFileGroup);
        res.setStatus(OperandiJobStatus.ACCEPTED);
        res.setWorkflowId(workflowId);
        return res;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getOperandiJobId() {
        return operandiJobId;
    }

    public void setOperandiJobId(String operandiJobId) {
        this.operandiJobId = operandiJobId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public OperandiJobStatus getStatus() {
        return status;
    }

    public void setStatus(OperandiJobStatus status) {
        this.status = status;
    }

    public String getPidResult() {
        return pidResult;
    }

    public void setPidResult(String pidResult) {
        this.pidResult = pidResult;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public String getInputFileGroup() {
        return inputFileGroup;
    }

    public void setInputFileGroup(String inputFileGroup) {
        this.inputFileGroup = inputFileGroup;
    }

    public Integer getCheckCounter() {
        return checkCounter;
    }

    public void setCheckCounter(Integer checkCounter) {
        this.checkCounter = checkCounter;
    }

    public Boolean getWorkspaceDeleted() {
        return workspaceDeleted;
    }

    public void setWorkspaceDeleted(Boolean workspaceDeleted) {
        this.workspaceDeleted = workspaceDeleted;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }
}
