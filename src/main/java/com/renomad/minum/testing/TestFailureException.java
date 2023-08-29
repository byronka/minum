package com.renomad.minum.testing;

import java.io.Serial;

/**
 * Thrown when a test fails
 */
public final class TestFailureException extends RuntimeException{

    @Serial
    private static final long serialVersionUID = 2937719847418284951L;

    /**
     * This constructor allows you to provide a text message
     * for insight into what exceptional situation took place.
     */
    public TestFailureException(String msg) {
        super(msg);
    }
}
