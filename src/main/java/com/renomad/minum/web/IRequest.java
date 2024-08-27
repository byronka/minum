package com.renomad.minum.web;

/**
 * An interface for {@link Request}. Built
 * to enable easier testing on web handlers.
 *
 */
public interface IRequest {

    Headers getHeaders();

    /**
     * Obtain information about the first line.  An example
     * of a RequestLine is: <pre>{@code GET /foo?bar=baz HTTP/1.1}</pre>
     */
    RequestLine getRequestLine();

    /**
     * This getter will process the body data fully on the first
     * call, and cache that data for subsequent calls.
     * <br>
     * If there is a need to deal with very large data, such as
     * large images or videos, consider using {@link #getSocketWrapper()}
     * instead, which will allow fine-grained control over pulling
     * the bytes off the socket.
     * <br>
     * For instance, if expecting a video (a large file), it may be prudent to store
     * the data into a file while it downloads, so that the server
     * does not need to hold the entire file in memory.
     */
    Body getBody();

    /**
     * Gets a string of the ip address of the client sending this
     * request.  For example, "123.123.123.123"
     */
    String getRemoteRequester();

    /**
     * This getter is expected to be used for situations required finer-grained
     * control over the socket, such as when dealing with streaming input like a game or chat,
     * or receiving a very large file like a video.  This will enable the user to
     * read and send on the socket - powerful, but requires care.
     * <p>
     * <br>
     * <em>Note:</em> This is an unusual method.
     * <br><br>
     * It is an error to call this in addition to {@link #getBody()}.  Use one or the other.
     * Mostly, expect to use getBody unless you really know what you are doing, such as
     * streaming situations or custom body protocols.
     * </p>
     */
    ISocketWrapper getSocketWrapper();

    /**
     * This method provides an {@link Iterable} for getting the key-value pairs of a URL-encoded
     * body in an HTTP request.  This method is intended to be used for situations
     * where the developer requires greater control, for example, if allowing large uploads
     * such as videos.
     * <br>
     * If this extra level of control is not needed, the developer would benefit more from
     * using the {@link #getBody()} method, which is far more convenient.
     */
    Iterable<UrlEncodedKeyValue> getUrlEncodedIterable();

    /**
     * This method provides an {@link Iterable} for getting the partitions of a multipart-form
     * formatted body in an HTTP request.  This method is intended to be used for situations
     * where the developer requires greater control, for example, if allowing large uploads
     * such as videos.
     * <br>
     * If this extra level of control is not needed, the developer would benefit more from
     * using the {@link #getBody()} method, which is far more convenient.
     */
    Iterable<StreamingMultipartPartition> getMultipartIterable();
}
