package ola.hd.longtermstorage.domain;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import ola.hd.longtermstorage.Constants;
import ola.hd.longtermstorage.controller.importarchive.FormParams;
import ola.hd.longtermstorage.utils.Utils;
import org.apache.commons.lang3.StringUtils;

/**
 * Class to save the additional configuration for indexing documents. In bag-info.txt special tags
 * can be provided to configure the indexing
 */
public class BaginfoConfig {

    /** File-Group to be used for indexing images */
    private String imageFileGrp = null;
    /** File-Group to be used for indexing fulltexts */
    private String fulltextFileGrp = null;
    /** File-Type of the fulltexts */
    private String fulltextFtype = null;
    /** Flag to show that data is Ground Truth */
    private Boolean gt = null;
    /** Who imported the work */
    private String importer = null;
    /** Identifier of the work, not unique through institutions */
    private String workIdentifier = null;
    /** PID of previous version */
    private String prevPid = null;
    /** "Global" unique identifier of the bag*/
    private String ocrdIdentifier = null;

    private BaginfoConfig() {
        // pass
    }

    /**
     * Read configuration from bag-info.txt.
     *
     * In bag-info.txt configuration for importing and indexing can be provided.
     *
     * @param bagInfos   Key-Values of bag-info.txt
     * @param formParams Form parameters ofimport request
     * @return
     */
    public static BaginfoConfig create(List<SimpleImmutableEntry<String, String>> bagInfos) {
        BaginfoConfig res = new BaginfoConfig();

        // read data from bag-info.txt-keys
        for (SimpleImmutableEntry<String, String> x : bagInfos) {
            if (StringUtils.isBlank(x.getValue()) || Utils.isNullValue(x.getValue())) {
                continue;
            }
            if (Constants.BAGINFO_KEY_IMAGE_FILEGRP.equals(x.getKey())) {
                res.setImageFileGrp(x.getValue());
            } else if (Constants.BAGINFO_KEY_FULLTEXT_FILEGRP.equals(x.getKey())) {
                res.setFulltextFileGrp(x.getValue());
            } else if (Constants.BAGINFO_KEY_IS_GT.equals(x.getKey())) {
                res.setGt(Utils.stringToBool(x.getValue()));
            } else if (Constants.BAGINFO_KEY_FTYPE.equals(x.getKey())) {
                res.setFulltextFtype(x.getValue());
            } else if (Constants.BAGINFO_KEY_WORK_IDENTIFIER.equals(x.getKey())) {
                res.setWorkIdentifier(x.getValue());
            } else if (Constants.BAGINFO_KEY_OCRD_IDENTIFIER.equals(x.getKey())) {
                res.setOcrdIdentifier(x.getValue());
            } else if (Constants.BAGINFO_KEY_PREV_PID.equals(x.getKey())) {
                res.setPrevPid(x.getValue());
            } else if (Constants.BAGINFO_KEY_IMPORTER.equals(x.getKey())) {
                res.setImporter(x.getValue());
            }
        }
        if (res.getImageFileGrp() == null) {
            res.setImageFileGrp(Constants.DEFAULT_IMAGE_FILEGRP);
        }
        if (res.getFulltextFileGrp() == null) {
            res.setFulltextFileGrp(Constants.DEFAULT_FULLTEXT_FILEGRP);
        }
        if (res.getFulltextFtype() == null) {
            res.setFulltextFtype(Constants.DEFAULT_FULLTEXT_FTYPE);
        }

        return res;
    }

    /**
     * Overwrite bag-info-parameters with form-parmeters, which are taken into account first
     *
     * @param formParams
     */
    public BaginfoConfig considerFormParams(FormParams formParams) {
        if (formParams.getIsGt() != null) {
            this.gt = formParams.getIsGt();
        }
        if (formParams.getFulltextFilegrp() != null) {
            this.fulltextFileGrp = formParams.getFulltextFilegrp();
        }
        if (formParams.getFulltextFtype() != null) {
            this.fulltextFtype = formParams.getFulltextFtype();
        }
        if (formParams.getImageFilegrp() != null) {
            this.imageFileGrp = formParams.getImageFilegrp();
        }
        if (formParams.getPrev() != null) {
            this.prevPid = formParams.getPrev();
        }
        return this;
    }

    public String getImageFileGrp() {
        return imageFileGrp;
    }

    public void setImageFileGrp(String imageFileGrp) {
        this.imageFileGrp = imageFileGrp;
    }

    public String getFulltextFileGrp() {
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

    public String getImporter() {
        return importer;
    }

    public void setImporter(String importer) {
        this.importer = importer;
    }

    public String getWorkIdentifier() {
        return workIdentifier;
    }

    public void setWorkIdentifier(String workIdentifier) {
        this.workIdentifier = workIdentifier;
    }

    public String getPrevPid() {
        return prevPid;
    }

    public void setPrevPid(String prevPid) {
        this.prevPid = prevPid;
    }

    public String getOcrdIdentifier() {
        return ocrdIdentifier;
    }

    public void setOcrdIdentifier(String ocrdIdentifier) {
        this.ocrdIdentifier = ocrdIdentifier;
    }
}
