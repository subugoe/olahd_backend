package ola.hd.longtermstorage.msg;

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
    public static final String ID_NOT_FOUND = "no archive available for provided id";
    public static final String METS_NOT_FOUND = "mets-file not found in archive";
}
