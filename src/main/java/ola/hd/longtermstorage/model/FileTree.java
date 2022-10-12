package ola.hd.longtermstorage.model;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Document(indexName = "fileTree", type = "fileTree")
public class FileTree {
    @Field(type = FieldType.Nested)
    Data data;

    public FileTree(Data data) {
        this.data = data;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    private static class Data {
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

        private static class FileGroups {
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
    }
}
