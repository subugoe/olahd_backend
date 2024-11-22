package de.ocrd.olahd.msg;

import de.ocrd.olahd.elasticsearch.ElasticQueryHelper;

/**
 * Constants for error-messages which are used more than once
 */
public class ErrMsg {

    private ErrMsg() {
    }

    public static final String ARCHIVE_NOT_FOUND = "Archive not found";
    public static final String FILE_NOT_FOUND = "File not found";
    public static final String PARAM_ID_IS_EMPTY = "Parameter id may not be empty";
    public static final String PARAM_PATH_IS_EMPTY = "Parameter path may not be empty";
    public static final String ID_NOT_FOUND = "No archive available for provided id";
    public static final String ID_NOT_FOUND_ONLINE = "No online archive available for provided id";
    public static final String METS_NOT_FOUND = "Mets-file not found in archive";
    public static final String METS_CONVERT_ERROR = "Error converting Mets-file FLocats";
    public static final String RECORD_NOT_FOUND = "Record not found";
    public static final String ID_OR_TERM_MISSING = "One of the request parameters id or searchterm are required";
    public static final String FULL_OR_METASEARCH = "Either 'metadatasearch' or 'fulltextsearch' must be true";
    public static final String FIELD_NOT_EQUALS_VALUE = "'field' and 'value' must be given the same number of times";
    public static final String UNKNOWN_FILTER = "'field' contains unknown filter. Valid are: " + String.join(", ", ElasticQueryHelper.FILTER_MAP.keySet());
    public static final String OCRD_IDENTIFIER_NOT_FOUND = "Ocrd-Identifier not found";
    public static final String PATH_NOT_A_TIF = "Parameter path must reference a tiff image";
    public static final String FILE_NOT_A_TIFF = "Given file is not a tiff image";
    public static final String TIFF_CONVERT_ERROR = "Error converting tiff to jpeg";
    public static final String IIIF_MANIFEST_NOT_FOUND = "IIIF-Manifest not found";


}
