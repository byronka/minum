package com.renomad.minum.templating;

import java.io.Serial;

/**
 * This exception is thrown when we try to render a string
 * template but fail to include a key for one of the key
 * values - that is, if the template is "hello {foo}", and
 * our map doesn't include a value for foo, this exception
 * will get thrown.
 */
public final class TemplateRenderException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -6403838479988560085L;

    public TemplateRenderException(String msg) {
        super(msg);
    }
}
