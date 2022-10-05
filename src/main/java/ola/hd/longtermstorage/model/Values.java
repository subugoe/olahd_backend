package ola.hd.longtermstorage.model;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "values", type = "values")
public class Values {
    @Field(type = FieldType.Text)
    String value;
    @Field(type = FieldType.Integer)
    int occurences;

    public Values(String value, int occurences) {
        this.value = value;
        this.occurences = occurences;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getOccurences() {
        return occurences;
    }

    public void setOccurences(int occurences) {
        this.occurences = occurences;
    }
}
