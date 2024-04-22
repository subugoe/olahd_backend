package ola.hd.longtermstorage;

import java.util.Arrays;
import java.util.List;

/**
 * Constants for the whole application
 *
 */
public class Constants {
    public final static String LOGICAL_INDEX_NAME = "meta.olahds_log";
    public final static String PHYSICAL_INDEX_NAME = "meta.olahds_phys";

    public static final String DEFAULT_IMAGE_FILEGRP = "OCR-D-IMG";
    public static final String DEFAULT_FULLTEXT_FILEGRP = "OCR-D-GT-SEG-LINE";
    public static final String DEFAULT_FULLTEXT_FTYPE = "PAGEXML_1";

    public static final List<String> POSSIBLE_FULLTEXT_FTYPES = Arrays
            .asList(new String[] { "PAGEXML_1", "ALTO_1", "TEI_2", "TEI_2a" });

    public static final String BAGINFO_KEY_PAYLOAD_OXUM = "Payload-Oxum";
    public static final String BAGINFO_KEY_OCRD_IDENTIFIER = "Ocrd-Identifier";

    public static final String BAGINFO_KEY_WORK_IDENTIFIER = "Ocrd-Work-Identifier";
    public static final String BAGINFO_KEY_IMAGE_FILEGRP = "Olahd-Search-Image-Filegrp";
    public static final String BAGINFO_KEY_FULLTEXT_FILEGRP = "Olahd-Search-Fulltext-Filegrp";
    public static final String BAGINFO_KEY_FTYPE = "Olahd-Search-Fulltext-Ftype";
    public static final String BAGINFO_KEY_PREV_PID = "Olahd-Search-Prev-PID";
    public static final String BAGINFO_KEY_IMPORTER = "Olahd-Importer";
    public static final String BAGINFO_KEY_IS_GT = "Olahd-GT";

    public static final String PAYLOAD_MANIFEST_NAME = "manifest-sha512.txt";

    private Constants() {
        //pass
    };
}
