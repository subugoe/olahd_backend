package ola.hd.longtermstorage.domain;

/**
 * Class to save the additional configuration for indexing documents. In bag-info.txt special
 * tags can be provided to configure the indexing
 */
public class IndexingConfig {

    /** File-Group to be used for indexing images*/
    private String imageFileGrp = null;
    /** File-Group to be used for indexing fulltexts*/
    private String fulltextFileGrp = null;
    /** File-Type of the fulltexts */
    private String fulltextFtype = null;
    /** Flag to show that data is Ground Truth*/
    private Boolean gt = null;

    public String getImageFileGrp() {
        // TODO:
        //imageFileGrp = ObjectUtils.firstNonNull(imageFileGrp, Constants.DEFAULT_IMAGE_FILEGRP);
        return imageFileGrp;
    }
    public void setImageFileGrp(String imageFileGrp) {
        this.imageFileGrp = imageFileGrp;
    }
    public String getFulltextFileGrp() {
        // TODO:
        //fulltextFileGrp = ObjectUtils.firstNonNull(fulltextFileGrp, Constants.DEFAULT_FULLTEXT_FILEGRP);
        return fulltextFileGrp;
    }
    public void setFulltextFileGrp(String fulltextFileGrp) {
        this.fulltextFileGrp = fulltextFileGrp;
    }
    public String getFulltextFtype() {
        return fulltextFtype;
    }
    public void setFulltextFtype(String fulltextFtype) {
        this.fulltextFtype = fulltextFtype;
    }
    public Boolean getGt() {
        return gt;
    }
    public void setGt(Boolean gt) {
        this.gt = gt;
    }
}
