package de.ocrd.olahd.domain;

import java.util.AbstractMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class ImportResult {

    private String onlineId;
    private String offlineId;
    private List<AbstractMap.SimpleImmutableEntry<String, String>> metaData;
    /** A blank offlineId already indicates tape is not used, this is just to make it even clearer*/
    private boolean tapeStorageUsed;

    public ImportResult(String onlineId, String offlineId, List<AbstractMap.SimpleImmutableEntry<String, String>> metaData) {
        this.onlineId = onlineId;
        this.metaData = metaData;
        if (StringUtils.isBlank(offlineId)) {
            this.tapeStorageUsed = false;
            this.offlineId = null;
        } else {
            this.offlineId = offlineId;
            this.tapeStorageUsed = true;
        }
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

    public List<AbstractMap.SimpleImmutableEntry<String, String>> getMetaData() {
        return metaData;
    }

    public void setMetaData(List<AbstractMap.SimpleImmutableEntry<String, String>> metaData) {
        this.metaData = metaData;
    }

    public boolean isTapeStorageUsed() {
        return tapeStorageUsed;
    }

    public void setTapeStorageUsed(boolean tapeStorageUsed) {
        this.tapeStorageUsed = tapeStorageUsed;
    }
}