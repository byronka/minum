package com.renomad.minum.templating;

import java.io.Serial;

/**
 * This exception is thrown for any failures during the rendering
 * of a template.
 */
public final class TemplateRenderException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -6403838479988560085L;

    public TemplateRenderException(String msg) {
        super(msg);
    }
}
