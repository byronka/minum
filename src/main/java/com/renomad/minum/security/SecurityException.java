package com.renomad.minum.security;

import java.io.Serial;

/**
 * Same as {@link RuntimeException} but scoped to the security package
 */
public class SecurityException extends RuntimeException {


    @Serial
    private static final long serialVersionUID = 1812161417927968297L;

    /**
     * See {@link RuntimeException#RuntimeException(String)}
     */
    public SecurityException(String message) {

    }
}
