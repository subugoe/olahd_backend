package ola.hd.longtermstorage.domain;

import java.io.File;

/**
 * Class for form parameters to query import endpoint
 */
public class BagImportParams {

    private File file = null;
    private String prev = null;
    private Boolean isGt = null;
    private String fulltextFilegrp = null;
    private String fulltextFtype = null;
    private String imageFilegrp = null;

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

    public Boolean getIsGt() {
        return isGt;
    }

    public void setIsGt(Boolean isGt) {
        this.isGt = isGt;
    }

    public String getFulltextFilegrp() {
        return fulltextFilegrp;
    }

    public void setFulltextFilegrp(String fulltextFilegrp) {
        this.fulltextFilegrp = fulltextFilegrp;
    }

    public String getImageFilegrp() {
        return imageFilegrp;
    }

    public void setImageFilegrp(String imageFilegrp) {
        this.imageFilegrp = imageFilegrp;
    }

    public String getFulltextFtype() {
        return fulltextFtype;
    }

    public void setFulltextFtype(String fulltextFtype) {
        this.fulltextFtype = fulltextFtype;
    }

}