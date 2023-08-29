package com.renomad.minum.htmlparsing;

import java.io.Serial;

/**
 * Thrown If a failure takes place during parsing in any
 * of the parsing code of the framework.
 */
public final class ParsingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 9158387443482452528L;

    /**
     * This constructor allows you to provide a text message
     * for insight into what exceptional situation took place.
     */
    public ParsingException(String msg) {
        super(msg);

    }

    /**
     * This constructor allows you to provide a text message
     * for insight into what exceptional situation took place.
     * @param cause - the inner cause of the exception
     */
    public ParsingException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
