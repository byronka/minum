package com.renomad.minum.web;

import java.util.Map;

/**
 * An interface for {@link Response}. Built
 * to enable easier testing on web handlers.
 */
public interface IResponse {
    /**
     * Any extra headers set on the Response by the developer
     */
    Map<String, String> getExtraHeaders();

    /**
     * The {@link com.renomad.minum.web.StatusLine.StatusCode} set by the developer
     * for this Response.
     */
    StatusLine.StatusCode getStatusCode();

    /**
     * Returns the bytes of the Response body being sent to the client
     */
    byte[] getBody();
}
