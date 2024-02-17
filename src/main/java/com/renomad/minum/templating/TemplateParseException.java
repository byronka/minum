package com.renomad.minum.templating;

import java.io.Serial;

/**
 * Thrown when failing to parse something in a template
 */
public final class TemplateParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -8784893791425360686L;

    public TemplateParseException(String msg) {
        super(msg);
    }
}
