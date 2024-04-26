package ola.hd.longtermstorage.exceptions;

/**
 * Thrown when the mets-schema cannot be read
 *
 * I had this problem when loc.gov was under maintenance once. I assume than one url was not available and because of
 * that an error was thrown when trying to read the schema properly.
 */
public class MetsSchemaException extends RuntimeException {

    private static final long serialVersionUID = -1384827841132180199L;

    public MetsSchemaException(String string, Exception e) {
        super(string, e);
    }



}
