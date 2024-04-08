package ola.hd.longtermstorage.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "archive")
public class Archive {

    @Id
    private String id;

    // PID of the archive
    private String pid;

    // CDSTAR-ID of an online archive
    private String onlineId;

    // CDSTAR-ID of an offline archive
    private String offlineId;

    @DBRef(lazy = true)
    private Archive previousVersion;

    @DBRef(lazy = true)
    private List<Archive> nextVersions;

    private String ocrdIdentifier;

    private String payloadOxum;

    @CreatedDate
    private LocalDateTime createdAt;

    protected Archive() {
        // no-args constructor required by JPA spec
        // this one is protected since it shouldn't be used directly
    }

    public Archive(String pid, String onlineId, String offlineId, String ocrdIdentifier, String payloadOxum) {
        this.pid = pid;
        this.onlineId = onlineId;
        this.offlineId = offlineId;
        this.ocrdIdentifier = ocrdIdentifier;
        this.payloadOxum = payloadOxum;
    }

    public void addNextVersion(Archive nextVersion) {
        if (nextVersions == null) {
            nextVersions = new ArrayList<>();
        }
        nextVersions.add(nextVersion);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getOnlineId() {
        return onlineId;
    }

    public void setOnlineId(String onlineId) {
        this.onlineId = onlineId;
    }

    public String getOfflineId() {
        return offlineId;
    }

    public void setOfflineId(String offlineId) {
        this.offlineId = offlineId;
    }

    public Archive getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(Archive previousVersion) {
        this.previousVersion = previousVersion;
    }

    public List<Archive> getNextVersions() {
        return nextVersions;
    }

    public void setNextVersions(List<Archive> nextVersions) {
        this.nextVersions = nextVersions;
    }

    public String getOcrdIdentifier() {
        return ocrdIdentifier;
    }

    public void setOcrdIdentifier(String ocrdIdentifier) {
        this.ocrdIdentifier = ocrdIdentifier;
    }

    public String getPayloadOxum() {
        return payloadOxum;
    }

    public void setPayloadOxum(String payloadOxum) {
        this.payloadOxum = payloadOxum;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
