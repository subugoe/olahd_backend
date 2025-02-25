package de.ocrd.olahd.domain;

public class RunOperandiWorkflowResponse {

    private String jobId = null;
    private String msg = null;
    private String pid = null;

    public RunOperandiWorkflowResponse(String jobId, String pid, String msg) {
        super();
        this.jobId = jobId;
        this.msg = msg;
        this.pid = pid;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

}
