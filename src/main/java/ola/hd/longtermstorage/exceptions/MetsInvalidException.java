package ola.hd.longtermstorage.exceptions;

public class MetsInvalidException extends RuntimeException {

    private static final long serialVersionUID = -6884444255973714055L;
    private String msg;

    public MetsInvalidException(String msg) {
        super("METS is invalid");
        this.msg = msg;
    }

    public String getMetsErrorMessage() {
        return msg;
    }
}
