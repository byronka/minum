package com.renomad.minum.web;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An interface for {@link Response}. Built
 * to enable easier testing on web handlers.
 */
public interface IResponse {
    /**
     * Any extra headers set on the Response by the developer
     */
    Collection<Map.Entry<String, String>> getExtraHeaders();

    /**
     * A convenient method to get the values in the {@link #getExtraHeaders()} given the header key
     */
    default List<String> getExtraHeader(String key) {
        return getExtraHeaders()
                .stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * The {@link com.renomad.minum.web.StatusLine.StatusCode} set by the developer
     * for this Response.
     */
    StatusLine.StatusCode getStatusCode();

    /**
     * Whether the body is text (rather than binary)
     * If true, a method like {@link Response#buildResponse(StatusLine.StatusCode, Map, String)}
     * was used, meaning the body would benefit from compression (if large enough
     * to warrant the performance hit from compressing it)
     */
    boolean isBodyText();

    /**
     * Gets the length of the body for this response.  If the body
     * is an array of bytes set by the user, we grab this value by the
     * length() method.  If the outgoing data is set by a lambda, the user
     * will set the bodyLength value.
     */
    long getBodyLength();

    /**
     * By calling this method with a {@link ISocketWrapper} parameter, the method
     * will send bytes on the associated socket.
     */
    void sendBody(ISocketWrapper sw) throws IOException;

    /**
     * Returns the bytes of the Response body being sent to the client
     */
    byte[] getBody();
}
