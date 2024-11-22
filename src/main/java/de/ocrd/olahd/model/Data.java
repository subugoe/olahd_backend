package de.ocrd.olahd.model;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

public class Data {
    @Field(type = FieldType.Text)
    String metsFile;
    List<String> bagitfiles;
    @Field(type = FieldType.Nested)
    FileGroups fileGroups;

    public Data(String metsFile, List<String> bagitFiles, FileGroups fileGroups) {
        this.metsFile = metsFile;
        this.bagitfiles = bagitFiles;
        this.fileGroups = fileGroups;
    }

    public String getMetsFile() {
        return metsFile;
    }

    public void setMetsFile(String metsFile) {
        this.metsFile = metsFile;
    }

    public List<String> getBagitfiles() {
        return bagitfiles;
    }

    public void setBagitfiles(List<String> bagitfiles) {
        this.bagitfiles = bagitfiles;
    }

    public FileGroups getFileGroups() {
        return fileGroups;
    }

    public void setFileGroups(FileGroups fileGroups) {
        this.fileGroups = fileGroups;
    }
}
