package com.renomad.minum.utils;

import java.io.Serial;

/**
 * An exception class used for some utility method scenarios
 */
public class UtilsException extends RuntimeException{

    @Serial
    private static final long serialVersionUID = -7328610036937226491L;

    /**
     * See {@link RuntimeException#RuntimeException(Throwable)}
     */
    public UtilsException(Throwable cause) {
        super(cause);
    }

    public UtilsException(String msg) {
        super(msg);
    }

}
