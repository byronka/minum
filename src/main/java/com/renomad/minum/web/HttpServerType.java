package com.renomad.minum.web;

/**
 * An enum to represent the mode of conversation for HTTP -
 * plain text or encrypted (TLS)
 */
public enum HttpServerType {
    /**
     * Represents a plain text, non-encrypted HTTP conversation
     */
    PLAIN_TEXT_HTTP,

    /**
     * Represents an HTTPS encrypted TLS conversation
     */
    ENCRYPTED_HTTP,

    /**
     * Indicates an unknown state
     */
    UNKNOWN,
}
