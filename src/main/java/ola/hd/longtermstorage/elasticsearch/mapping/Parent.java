package ola.hd.longtermstorage.elasticsearch.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Parent {

    @JsonProperty("record_identifier")
    private String recordIdentifier = null;

    public String getRecordIdentifier() {
        return recordIdentifier;
    }

    public void setRecordIdentifier(String recordIdentifier) {
        this.recordIdentifier = recordIdentifier;
    }
}
