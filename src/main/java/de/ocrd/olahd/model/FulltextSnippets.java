package de.ocrd.olahd.model;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "fulltextSnippets", type = "fulltextSnippets")
public class FulltextSnippets {

    @Field(type = FieldType.Text)
    private String value;
    @Field(type = FieldType.Integer)
    private int page;

    public FulltextSnippets(String value, int page) {
        this.value = value;
        this.page = page;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
