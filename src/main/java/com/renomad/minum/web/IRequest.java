package com.renomad.minum.web;


/**
 * An HTTP request.
 * <p>
 * From <a href="https://en.wikipedia.org/wiki/HTTP#Request_syntax">Wikipedia</a>
 * </p>
 *<p>
 *     A client sends request messages to the server, which consist of:
 *</p>
 * <ul>
 *     <li>
 *      a request line, consisting of the case-sensitive request
 *      method, a space, the requested URL, another space, the
 *      protocol version, a carriage return, and a line feed, e.g.:
 *      <pre>
 *          GET /images/logo.png HTTP/1.1
 *      </pre>
 *      </li>
 *
 *      <li>
 *      zero or more request header fields (at least 1 or more
 *      headers in case of HTTP/1.1), each consisting of the case-insensitive
 *      field name, a colon, optional leading whitespace, the field
 *      value, an optional trailing whitespace and ending with a
 *      carriage return and a line feed, e.g.:
 *
 *      <pre>
 *      Host: www.example.com
 *      Accept-Language: en
 *      </pre>
 *      </li>
 *
 *      <li>
 *      an empty line, consisting of a carriage return and a line feed;
 *      </li>
 *
 *      <li>
 *      an optional message body.
 *      </li>
 *      In the HTTP/1.1 protocol, all header fields except Host: hostname are optional.
 * </ul>
 *
 * <p>
 * A request line containing only the path name is accepted by servers to
 * maintain compatibility with HTTP clients before the HTTP/1.0 specification in RFC 1945.
 *</p>
 *
 */
public interface IRequest {

    /**
     * Get a {@link Headers} object, which contains all the headers in the request
     */
    Headers getHeaders();

    /**
     * Obtain information about the first line.  An example
     * of a RequestLine is: <pre>{@code GET /foo?bar=baz HTTP/1.1}</pre>
     * <p>
     *     This is where you look for anything in the request line, like
     *     paths or query strings.
     * </p>
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
     * <h3>Further explanation:</h3>
     * <p>
     *     The body contains the data beyond the initial status line and headers.
     *     For example, you will find HTML, images, and other bulk data stored
     *     in the body.  If you are expecting data sent in a POST, it is usually
     *     found in the body.
     * </p>
     * <p>
     *     By calling this method, the entire body contents will be read.  In the case
     *     of ordinary-sized data, this is not a problem.  But, for the sake of explanation,
     *     if the data were a gigabyte in size, then your server would need to set aside
     *     that much in memory for it.
     * </p>
     * <p>
     *     It is therefore a security concern, and the defaults of the Minum system are
     *     set to prevent security failures.  It is recommended that the developer
     *     handle such large data by another means, such as by using the aforementioned {@link #getSocketWrapper()}.
     *     By default, the system will not read the body if the content-length is greater
     *     than the size specified by MAX_READ_SIZE_BYTES, described in the Constants class and
     *     in the "minum.config" file.
     * </p>
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
