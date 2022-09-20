package ola.hd.longtermstorage.exceptions;


/**
 * For Errors related to the Elasticsearch-Service
 */
public class ElasticServiceException extends RuntimeException {

    private static final long serialVersionUID = -3773459224669213853L;

    public ElasticServiceException() {
        super();
    }

    public ElasticServiceException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public ElasticServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ElasticServiceException(String message) {
        super(message);
    }

    public ElasticServiceException(Throwable cause) {
        super(cause);
    }
}
