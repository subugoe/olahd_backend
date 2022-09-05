package ola.hd.longtermstorage.domain;

public class DownloadRequest {

    private String archiveId;

    private String[] files;

    private boolean internalId = true;

    public DownloadRequest() {
    }

    public String getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(String archiveId) {
        this.archiveId = archiveId;
    }

    public boolean isInternalId() {
        return internalId;
    }

    public void setInternalId(boolean internalId) {
        this.internalId = internalId;
    }

    public String[] getFiles() {
        return files;
    }

    public void setFiles(String[] files) {
        this.files = files;
    }
}
