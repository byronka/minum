package com.renomad.minum.web;

/**
 * This exception is thrown if the range of bytes provided for a request
 * is improper - such as if the range values were negative, wrongly-ordered,
 * and so on.
 */
public class InvalidRangeException extends RuntimeException {
    public InvalidRangeException(String msg) {
        super(msg);
    }
}
