package de.ocrd.olahd.exceptions;

import de.ocrd.olahd.operandi.OperandiService;

/**
 * This exception is thrown by the {@linkplain OperandiService}
 */
public class OperandiException extends RuntimeException {

    private static final long serialVersionUID = -8080471598533081246L;

    public OperandiException(String message, Throwable cause) {
        super(message, cause);
    }

    public OperandiException(String message) {
        super(message);
    }

    public OperandiException(Throwable cause) {
        super(cause);
    }

}
