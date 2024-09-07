package com.renomad.minum.web;

import com.renomad.minum.utils.StringUtils;

import java.util.*;

/**
 * This class represents the body of an HTML message.
 * See <a href="https://en.wikipedia.org/wiki/HTTP_message_body">Message Body on Wikipedia</a>
 *<br>
 * <pre>{@code
 * This could be a response from the web server:
 *
 * HTTP/1.1 200 OK
 * Date: Sun, 10 Oct 2010 23:26:07 GMT
 * Server: Apache/2.2.8 (Ubuntu) mod_ssl/2.2.8 OpenSSL/0.9.8g
 * Last-Modified: Sun, 26 Sep 2010 22:04:35 GMT
 * ETag: "45b6-834-49130cc1182c0"
 * Accept-Ranges: bytes
 * Content-Length: 12
 * Connection: close
 * Content-Type: text/html
 *
 * Hello world!
 * }</pre>
 * <p>
 *     The message body (or content) in this example is the text <pre>Hello world!</pre>.
 * </p>
 */
public final class Body {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private final Map<String, byte[]> bodyMap;
    private final byte[] raw;
    private final List<Partition> partitions;
    private final BodyType bodyType;

    /**
     * An empty body instance, useful when you
     * need an instantiated body.
     */
    public static final Body EMPTY = new Body(Map.of(), EMPTY_BYTES, List.of(), BodyType.NONE);

    /**
     * Build a body for an HTTP message
     * @param bodyMap a map of key-value pairs, presumably extracted from form data.  Empty
     *                if our body isn't one of the form data protocols we understand.
     * @param raw the raw bytes of this body
     * @param partitions if the body is of type form/multipart, these will be the list of partitions
     */
    public Body(Map<String, byte[]> bodyMap, byte[] raw, List<Partition> partitions, BodyType bodyType) {
        this.bodyMap = new HashMap<>(bodyMap);
        this.raw = raw.clone();
        this.partitions = partitions;
        this.bodyType = bodyType;
    }

    /**
     * Return the value for a key, as a string. This method
     * presumes the data was sent URL-encoded.
     * <p>
     *     If there is no value found for the
     *     provided key, an empty string will be
     *     returned.
     * </p>
     * <p>
     *     Otherwise, the value found will be converted
     *     to a string, and trimmed.
     * </p>
     * <p>
     *     Note: if the request is a multipart/form-data, this
     *     method will throw a helpful exception to indicate that.
     * </p>
     *
     */
    public String asString(String key) {
        if (this.equals(EMPTY)) {
            return "";
        }
        if (this.bodyType.equals(BodyType.MULTIPART)) {
            throw new WebServerException("Request body is in multipart format.  Use .getPartitionByName instead");
        }
        if (this.bodyType.equals(BodyType.UNRECOGNIZED)) {
            throw new WebServerException("Request body is not in a recognized key-value encoding.  Use .asString() to obtain the body data");
        }
        byte[] byteArray = bodyMap.get(key);
        if (byteArray == null) {
            return "";
        } else {
            return StringUtils.byteArrayToString(byteArray).trim();
        }

    }

    /**
     * Return the entire raw contents of the body of this
     * request, as a string. No processing involved other
     * than converting the bytes to a string.
     */
    public String asString() {
        if (this.equals(EMPTY)) {
            return "";
        }
        return StringUtils.byteArrayToString(raw).trim();
    }

    /**
     * Return the bytes of this request body by its key.  This method
     * presumes the data was sent URL-encoded.
     */
    public byte[] asBytes(String key) {
        if (this.equals(EMPTY)) {
            return new byte[0];
        }
        if (this.bodyType.equals(BodyType.MULTIPART)) {
            throw new WebServerException("Request body is in multipart format.  Use .getPartitionByName instead");
        }
        if (this.bodyType.equals(BodyType.UNRECOGNIZED)) {
            throw new WebServerException("Request body is not in a recognized key-value encoding.  Use .asBytes() to obtain the body data");
        }
        return bodyMap.get(key);
    }

    /**
     * Returns the raw bytes of this HTTP message's body. This method
     * presumes the data was sent URL-encoded.
     */
    public byte[] asBytes() {
        if (this.equals(EMPTY)) {
            return new byte[0];
        }
        return this.raw.clone();
    }

    /**
     * If the body is of type form/multipart, return the partitions
     * <p>
     *     For example:
     * </p>
     * <pre>
     * --i_am_a_boundary
     *  Content-Type: text/plain
     *  Content-Disposition: form-data; name="text1"
     *
     *  I am a value that is text
     *  --i_am_a_boundary
     *  Content-Type: application/octet-stream
     *  Content-Disposition: form-data; name="image_uploads"; filename="photo_preview.jpg"
     * </pre>
     */
    public List<Partition> getPartitionHeaders() {
        if (this.equals(EMPTY)) {
            return List.of();
        }
        if (this.bodyType.equals(BodyType.FORM_URL_ENCODED)) {
            throw new WebServerException("Request body encoded in form-urlencoded format. getPartitionHeaders is only used with multipart encoded data.");
        }
        if (this.bodyType.equals(BodyType.UNRECOGNIZED)) {
            throw new WebServerException("Request body encoded is not encoded in a recognized format. getPartitionHeaders is only used with multipart encoded data.");
        }
        return new ArrayList<>(partitions);
    }

    /**
     * A helper method for getting the partitions with a particular name set in its
     * content-disposition.  This returns a list of partitions because there is nothing
     * preventing the browser doing this, and in fact it will typically send partitions
     * with the same name when sending multiple files from one input.  (HTML5 provides the
     * ability to select multiple files on the input with type=file)
     */
    public List<Partition> getPartitionByName(String name) {
        if (this.equals(EMPTY)) {
            return List.of();
        }
        if (this.bodyType.equals(BodyType.FORM_URL_ENCODED)) {
            throw new WebServerException("Request body encoded in form-urlencoded format. use .asString(key) or asBytes(key)");
        }
        if (this.bodyType.equals(BodyType.UNRECOGNIZED)) {
            throw new WebServerException("Request body encoded is not encoded in a recognized format. use .asString() or asBytes()");
        }
        return getPartitionHeaders().stream().filter(x -> x.getContentDisposition().getName().equalsIgnoreCase(name)).toList();
    }

    /**
     * Returns the {@link BodyType}, which is necessary to distinguish
     * which methods to run for accessing data. For instance, if the body
     * is of type FORM_URL_ENCODED, you may use methods
     * like {@link #getKeys()}, {@link #asBytes(String)}, or {@link #asString(String)}
     * <br>
     * On the other hand, if the type is MULTIPART, you will need to use {@link #getPartitionHeaders()}
     * to get a list of the partitions.
     * <br>
     * If the body type is UNRECOGNIZED, you can use {@link #asBytes()} to get the body.
     * <br>
     * Don't forget, there is also an option to obtain the body's {@link java.io.InputStream} by
     * using {@link Request#getSocketWrapper()}, but that needs to be done before running {@link Request#getBody()}
     */
    public BodyType getBodyType() {
        return bodyType;
    }

    /**
     * Get all the keys for the key-value pairs in the body
     */
    public Set<String> getKeys() {
        if (this.equals(EMPTY)) {
            return Set.of();
        }
        return bodyMap.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Body body = (Body) o;
        return Objects.equals(bodyMap, body.bodyMap) && Arrays.equals(raw, body.raw) && Objects.equals(partitions, body.partitions) && bodyType == body.bodyType;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(bodyMap, partitions, bodyType);
        result = 31 * result + Arrays.hashCode(raw);
        return result;
    }

    @Override
    public String toString() {
        return "Body{" +
                "bodyMap=" + bodyMap +
                ", bodyType=" + bodyType +
                '}';
    }

}
