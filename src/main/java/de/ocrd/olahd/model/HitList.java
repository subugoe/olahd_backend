package de.ocrd.olahd.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public class HitList {

    @JsonProperty("PID")
    private String pid;
    @JsonProperty("ID")
    private String id;
    private String title;
    private String subtitle;
    private String placeOfPublish;
    private int yearOfPublish;
    private String publisher;
    private String creator;
    @JsonProperty("isGT")
    private Boolean gt;
    private FulltextSnippets fulltextSnippets;
    private Boolean noData;

    public HitList(String pid, String id, String title, String subtitle, String placeOfPublish, int yearOfPublish,
            String publisher, String creator, FulltextSnippets fulltextSnippets, Boolean gt) {
        this.pid = pid;
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.placeOfPublish = placeOfPublish;
        this.yearOfPublish = yearOfPublish;
        this.publisher = publisher;
        this.creator = creator;
        this.fulltextSnippets = fulltextSnippets;
        this.gt = gt;
    }

    public HitList() {

    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getPlaceOfPublish() {
        return placeOfPublish;
    }

    public void setPlaceOfPublish(String placeOfPublish) {
        this.placeOfPublish = placeOfPublish;
    }

    public int getYearOfPublish() {
        return yearOfPublish;
    }

    public void setYearOfPublish(int yearOfPublish) {
        this.yearOfPublish = yearOfPublish;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public FulltextSnippets getFulltextSnippets() {
        return fulltextSnippets;
    }

    public void setFulltextSnippets(FulltextSnippets fulltextSnippets) {
        this.fulltextSnippets = fulltextSnippets;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isGt() {
        return gt;
    }

    public void setGt(boolean gt) {
        this.gt = gt;
    }

    public Boolean getGt() {
        return gt;
    }

    public void setGt(Boolean gt) {
        this.gt = gt;
    }

    public Boolean getNoData() {
        return noData;
    }

    public void setNoData() {
        if (StringUtils.isBlank(this.title) && StringUtils.isBlank(this.subtitle)
                && StringUtils.isBlank(this.placeOfPublish) && yearOfPublish < 0
                && StringUtils.isBlank(this.publisher) && StringUtils.isBlank(this.creator)) {
            this.noData = true;
        } else {
            this.noData = false;
        }
    }
}
