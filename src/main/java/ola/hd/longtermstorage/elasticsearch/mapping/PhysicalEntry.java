package ola.hd.longtermstorage.elasticsearch.mapping;

import static ola.hd.longtermstorage.Constants.PHYSICAL_INDEX_NAME;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

/**
 * XXX: for future use. Not really used somewhere yet (remove comment or class if used or not needed)
 */
@Document(indexName = PHYSICAL_INDEX_NAME, type = "phys_type")
public class PhysicalEntry {

    @Id
    private String id;
    private String bycreator;
    private String bytitle;
    private String filename;
    private String fulltext;
    private String page;
    private Parent parent;

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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFulltext() {
        return fulltext;
    }

    public void setFulltext(String fulltext) {
        this.fulltext = fulltext;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public Parent getParent() {
        return parent;
    }

    public void setParent(Parent parent) {
        this.parent = parent;
    }


}

