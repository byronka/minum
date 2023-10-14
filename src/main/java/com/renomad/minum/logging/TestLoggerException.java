package com.renomad.minum.logging;

/**
 * This is an implementation of {@link RuntimeException}, scoped
 * for the TestLogger.
 */
public class TestLoggerException extends RuntimeException {

    /**
     * See {@link RuntimeException#RuntimeException(String)}
     */
    public TestLoggerException(String message) {
        super(message);
    }
}
