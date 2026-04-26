package com.renomad.minum.web;

import java.io.InputStream;

/**
 * An interface for the {@link BodyProcessor} implementation.
 * Solely created to provide better testing access
 */
public interface IBodyProcessor {

    /**
     * When parsing fails, we would like to send the raw text
     * back to the user so the development team can determine
     * why parsing went awry.  But, when we are sent a huge file,
     * we would rather not include all that data in the logs.
     * So we will cap out at this value.
     */
    int MAX_SIZE_DATA_RETURNED_IN_EXCEPTION = 1024;

    /**
     * The largest size name we will allow is 50 bytes, that is 50
     * ascii characters.  There is no way anyone would benefit from having
     * keys larger than this, unless they are attacking us, or using
     * the system in different ways than its intended design.
     */
    int MAX_KEY_SIZE_BYTES = 50;

    /**
     * Just providing a sane upper limit, with a little extra for safety factor.
     */
    int MAX_BODY_KEYS_URL_ENCODED = 1000;

    /**
     * read the body if one exists
     * <br>
     * There are really only two ways to read the body.
     * <ol>
     * <li>the client tells us how many bytes to read</li>
     * <li>the client uses "transfer-encoding: chunked"</li>
     * </ol>
     * <br>
     * <p>
     * <em>Note:</em> we don't read chunked data.
     * </p>
     * <p>
     * it is absolutely critical that the client gives us
     * a way to know ahead of time how many bytes to read, so we (the server)
     * can stop reading at precisely the right point.  There's simply no
     * other way to reasonably do this.
     * </p>
     */
    Body extractData(InputStream is, Headers h);

    /**
     * Return an iterable for stepping through the key-value pairs of URL-encoded data.
     * <br>
     * If the incoming Request body is URL-encoded, you may optionally use this method to
     * obtain the data more incrementally than by using {@link #extractData(InputStream, Headers)}, which
     * puts the entire data into memory.
     * @param inputStream The {@link InputStream} is set at the beginning of the body in the Request.  The first
     *                    read will return the first byte of body data.
     * @param contentLength The length of data in the body.  This is obtained from the content-length
     *                      header.
     */
    Iterable<UrlEncodedKeyValue> getUrlEncodedDataIterable(InputStream inputStream, long contentLength);


    /**
     * Return an iterable for stepping through the multipart partitions.
     * <br>
     * @param boundaryValue this is a string value, randomly-generated, from the user agent (i.e. the browser),
     *                      designating the edge of data partitions.  It can be found in the content-type header,
     *                      if the type is multipart.
     * @param inputStream The {@link InputStream} is set at the beginning of the body in the Request.  The first
     *                    read will return the first byte of body data.
     * @param contentLength The length of data in the body.  This is obtained from the content-length
     *                      header.
     */
    Iterable<StreamingMultipartPartition> getMultiPartIterable(InputStream inputStream, String boundaryValue, int contentLength);
}
