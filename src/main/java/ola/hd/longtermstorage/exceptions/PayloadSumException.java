package ola.hd.longtermstorage.exceptions;

/**
 * Exception is thrown when checksums in manifest-sha512.txt not fit to files
 */
public class PayloadSumException extends RuntimeException {

    private static final long serialVersionUID = -3308068427244532644L;

    public PayloadSumException() {
        super();
    }

    public PayloadSumException(String message, Throwable cause) {
        super(message, cause);
    }

    public PayloadSumException(String message) {
        super(message);
    }
}
