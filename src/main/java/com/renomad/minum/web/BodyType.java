package com.renomad.minum.web;

/**
 * The type of HTTP request body
 */
public enum BodyType {
    /**
     * a body exists but does not correspond to any known encoding
     */
    UNRECOGNIZED,

    /**
     * Indicates there is no body
     */
    NONE,

    /**
     * key-value pairs joined by ampersands, with the values encoded using
     * URL encoding, also known as percent encoding.
     * Look up application/x-www-form-urlencoded
     */
    FORM_URL_ENCODED,

    /**
     * Splits up the content into partitions separated by a boundary value
     * that is specified by sender.  Look up multipart/form-data
     */
    MULTIPART
}
