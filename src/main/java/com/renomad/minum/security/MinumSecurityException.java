package com.renomad.minum.security;

import java.io.Serial;

/**
 * A {@link RuntimeException} scoped to the security package
 */
public final class MinumSecurityException extends RuntimeException {


    @Serial
    private static final long serialVersionUID = 1812161417927968297L;

    /**
     * See {@link RuntimeException#RuntimeException(String)}
     */
    public MinumSecurityException(String message) {
        super(message);
    }
}
