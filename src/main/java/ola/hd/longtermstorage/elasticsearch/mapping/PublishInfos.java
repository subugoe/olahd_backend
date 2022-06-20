package ola.hd.longtermstorage.elasticsearch.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class PublishInfos {
    @JsonProperty("year_publish")
    private Integer yearPublish;
    @JsonProperty("place_publish")
    private List<String> placePublish;

    public Integer getYearPublish() {
        return yearPublish;
    }
    public void setYearPublish(Integer yearPublish) {
        this.yearPublish = yearPublish;
    }
    public List<String> getPlacePublish() {
        return placePublish;
    }
    public void setPlacePublish(List<String> placePublish) {
        this.placePublish = placePublish;
    }
}
