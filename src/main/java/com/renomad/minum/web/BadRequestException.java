package com.renomad.minum.web;

/**
 * An exception used for situations where the incoming request has data
 * that is invalid in some way.  The HTTP 400 Bad Request response corresponds
 * to this situation.  This is a catch-all exception for when the server
 * recognizes invalid data.
 * <br>
 * This is used in similar places as {@link com.renomad.minum.security.ForbiddenUseException},
 * except that ForbiddenUse is for situations which seem like potential attacks
 * rather than merely bad input.
 */
public final class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
