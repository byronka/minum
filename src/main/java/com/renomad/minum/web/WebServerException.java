package com.renomad.minum.web;

import java.io.IOException;

/**
 * This is just a {@link RuntimeException} that is scoped
 * for our web server.
 */
public class WebServerException extends RuntimeException{

    /**
     * See {@link RuntimeException#RuntimeException(Throwable)}
     */
    public WebServerException(Throwable cause) {
        super(cause);
    }
}
