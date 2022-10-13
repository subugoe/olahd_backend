package ola.hd.longtermstorage.model;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

public class FileGroups {
    @Field(type = FieldType.Text)
    String filegroupname;
    @Field(type = FieldType.Text)
    List<String> filenames;

    public FileGroups(String filegroupname, List<String> filenames) {
        this.filegroupname = filegroupname;
        this.filenames = filenames;
    }

    public String getFilegroupname() {
        return filegroupname;
    }

    public void setFilegroupname(String filegroupname) {
        this.filegroupname = filegroupname;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames;
    }
}
