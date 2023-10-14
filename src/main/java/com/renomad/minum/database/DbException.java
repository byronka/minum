package com.renomad.minum.database;

/**
 * Represents an exception that takes places in the {@link Db} code
 */
public class DbException extends RuntimeException {

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
