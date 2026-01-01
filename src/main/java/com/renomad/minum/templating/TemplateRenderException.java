package com.renomad.minum.templating;

import java.io.Serial;

/**
 * Thrown when there are any issues found in the templating
 */
public final class TemplateRenderException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -6403838479988560085L;

    public TemplateRenderException(String msg) {
        super(msg);
    }
}
