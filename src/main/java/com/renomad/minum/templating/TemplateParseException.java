package com.renomad.minum.templating;

import java.io.Serial;

/**
 * This exception is thrown when we try to convert a string
 * template into a list of {@link TemplateSection} and
 * fail to parse something.
 */
public final class TemplateParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -8784893791425360686L;

    public TemplateParseException(String msg) {
        super(msg);
    }
}
