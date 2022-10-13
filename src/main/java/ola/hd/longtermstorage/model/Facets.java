package ola.hd.longtermstorage.model;

import java.util.List;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "facets", type = "facets")
public class Facets {

    @Field(type = FieldType.Text)
    private String name;
    @Field(type = FieldType.Text)
    private List<Values> values;

    public Facets(String name, List<Values> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Values> getValues() {
        return values;
    }

    public void setValues(List<Values> values) {
        this.values = values;
    }

}
