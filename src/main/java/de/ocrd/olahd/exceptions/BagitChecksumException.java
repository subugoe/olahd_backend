package de.ocrd.olahd.exceptions;

import java.util.List;

/**
 * Exception indicates that checksums in tag or payload manifest are not matching.
 */
public class BagitChecksumException extends RuntimeException {

    private static final long serialVersionUID = -6862285407592802471L;

    private List<String> errors;

    public BagitChecksumException(List<String> errors) {
        super("Bagit checksum mismatch");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
