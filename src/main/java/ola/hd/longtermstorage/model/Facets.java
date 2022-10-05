package ola.hd.longtermstorage.model;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "facets", type = "facets")
public class Facets {

    @Field(type = FieldType.Text)
    String name;
    @Field(type = FieldType.Text)
    Values values;

    public Facets(String name, Values values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Values getValues() {
        return values;
    }

    public void setValues(Values values) {
        this.values = values;
    }
}
