package com.renomad.minum.web;

import java.io.Serial;

/**
 * This is just a {@link RuntimeException} that is scoped
 * for our web server.
 */
public class WebServerException extends RuntimeException{

    @Serial
    private static final long serialVersionUID = 3964129858639403836L;

    /**
     * See {@link RuntimeException#RuntimeException(Throwable)}
     */
    public WebServerException(Throwable cause) {
        super(cause);
    }
}
