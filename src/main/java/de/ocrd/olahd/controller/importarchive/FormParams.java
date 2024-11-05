package de.ocrd.olahd.controller.importarchive;

import java.io.File;

/**
 * This class contains the form-parameters provided with the POST request of the import
 */
public class FormParams {

    private File file = null;
    private String prev = null;

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getPrev() {
        return prev;
    }

    public void setPrev(String prev) {
        this.prev = prev;
    }

}