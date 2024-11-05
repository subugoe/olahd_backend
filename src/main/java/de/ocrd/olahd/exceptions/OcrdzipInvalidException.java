package de.ocrd.olahd.exceptions;

import java.util.List;

public class OcrdzipInvalidException extends RuntimeException {

    private static final long serialVersionUID = 4723099420658803963L;

    private List<String> errors;

    public OcrdzipInvalidException(List<String> errors) {
        super("OCRD-ZIP is invalid");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
