package com.renomad.minum.database;

import java.io.Serial;

/**
 * Exceptions that occur in the {@link Db}
 */
public final class DbException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -9063971131447186027L;

    /**
     * A {@link RuntimeException} scoped to
     * the Minum database package.  See {@link RuntimeException#RuntimeException(String)}
     */
    public DbException(String message) {
        super(message);
    }

    /**
     * A {@link RuntimeException} scoped to
     * the Minum database package.  See {@link RuntimeException#RuntimeException(String, Throwable)}
     */
    public DbException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * A {@link RuntimeException} scoped to
     * the Minum database package.  See {@link RuntimeException#RuntimeException(Throwable)}
     */
    public DbException(Throwable cause) {
        super(cause);
    }
}
