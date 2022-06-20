package ola.hd.longtermstorage.elasticsearch.mapping;

import static ola.hd.longtermstorage.Constants.LOGICAL_INDEX_NAME;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * record of "meta.log"-Index
 *
 * Every field of the index which should be queried must be a field of this class, but I only
 * started with the fields I need for the first queries
 *
 * XXX: for future use. Not really used somewhere yet (remove comment or class if used or not needed)
 */
@Document(indexName = LOGICAL_INDEX_NAME, type = "log_type")
public class LogicalEntry {

    @Id
    private String id;
    private String bycreator;
    private String bytitle;
    private Parent parent;
    @JsonProperty("publish_infos")
    private PublishInfos publishInfos;

    public String getBycreator() {
        return bycreator;
    }

    public void setBycreator(String bycreator) {
        this.bycreator = bycreator;
    }

    public String getBytitle() {
        return bytitle;
    }

    public void setBytitle(String bytitle) {
        this.bytitle = bytitle;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Parent getParent() {
        return parent;
    }

    public void setParent(Parent parent) {
        this.parent = parent;
    }

    public PublishInfos getPublishInfos() {
        return publishInfos;
    }

    public void setPublishInfos(PublishInfos publishInfos) {
        this.publishInfos = publishInfos;
    }


}

