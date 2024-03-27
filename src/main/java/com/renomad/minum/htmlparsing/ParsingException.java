package com.renomad.minum.htmlparsing;

import java.io.Serial;

/**
 * Thrown if a failure occurs parsing
 */
public final class ParsingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 9158387443482452528L;

    /**
     * Construct an exception during parsing
     * @param message an informative message for recipients
     */
    public ParsingException(String message) {
        super(message);

    }
}
