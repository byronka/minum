package com.renomad.minum.web;

import com.renomad.minum.utils.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_206_PARTIAL_CONTENT;

/**
 * Represents an HTTP response. This is what will get sent back to the
 * client (that is, to the browser).  There are a variety of overloads
 * of this record for different situations.  The overarching paradigm is
 * to provide you high flexibility.
 * <p>
 * A response message is sent by a server to a client as a reply to its former request message.
 * </p>
 * <h3>
 * Response syntax
 * </h3>
 * <p>
 * A server sends response messages to the client, which consist of:
 * </p>
 * <ul>
 * <li>
 * a status line, consisting of the protocol version, a space, the
 * response status code, another space, a possibly empty reason
 * phrase, a carriage return and a line feed, e.g.:
 * <pre>
 * HTTP/1.1 200 OK
 * </pre>
 * </li>
 *
 * <li>
 * zero or more response header fields, each consisting of the case-insensitive
 * field name, a colon, optional leading whitespace, the field value, an
 * optional trailing whitespace and ending with a carriage return and a line feed, e.g.:
 * <pre>
 * Content-Type: text/html
 * </pre>
 * </li>
 *
 * <li>
 * an empty line, consisting of a carriage return and a line feed;
 * </li>
 *
 * <li>
 * an optional message body.
 * </li>
 *</ul>

 */
public final class Response implements IResponse {

    private final StatusLine.StatusCode statusCode;
    private final Collection<Map.Entry<String, String>> extraHeaders;
    private final byte[] body;
    private final ThrowingConsumer<ISocketWrapper> outputGenerator;
    private final long bodyLength;

    /**
     * If true, the body is text, and therefore would probably benefit
     * from being compressed before sending (presuming sufficiently large
     * to make it worth the time for compression)
     */
    private final boolean isBodyText;

    /**
     * This is the constructor that provides access to all fields.  It is not intended
     * to be used from outside this class.
     * @param extraHeaders extra headers we want to return with the response.
     * @param outputGenerator a {@link ThrowingConsumer} that will use a {@link ISocketWrapper} parameter
     *                        to send bytes on the wire back to the client.  See the static factory methods
     *                        such as {@link #buildResponse(StatusLine.StatusCode, Collection, byte[])} for more details on this.
     * @param bodyLength this is used to set the content-length header for the response.  If this is
     *                   not provided, we set the header to "transfer-encoding: chunked", or in other words, streaming.
     */
    Response(StatusLine.StatusCode statusCode, Collection<Map.Entry<String, String>> extraHeaders, byte[] body,
             ThrowingConsumer<ISocketWrapper> outputGenerator, long bodyLength, boolean isBodyText) {
        this.statusCode = statusCode;
        this.extraHeaders = new ArrayList<>(extraHeaders);
        this.body = body;
        this.outputGenerator = outputGenerator;
        this.bodyLength = bodyLength;
        this.isBodyText = isBodyText;
    }

    /**
     * This factory method is intended for situations where the user wishes to stream data
     * but lacks the content length.  This is only for unusual situations where the developer
     * needs the extra control.  In most cases, other methods are more suitable.
     * @param extraHeaders any extra headers for the response, such as the content-type
     * @param outputGenerator a function that will be given a {@link ISocketWrapper}, providing the
     *                        ability to send bytes on the socket.
     */
    public static IResponse buildStreamingResponse(StatusLine.StatusCode statusCode, Collection<Map.Entry<String, String>> extraHeaders, ThrowingConsumer<ISocketWrapper> outputGenerator) {
        return new Response(statusCode, extraHeaders, null, outputGenerator, 0, false);
    }

    /**
     * This factory method is intended for situations where the user wishes to stream data
     * but lacks the content length.  This is only for unusual situations where the developer
     * needs the extra control.  In most cases, other methods are more suitable.
     * @param extraHeaders any extra headers for the response, such as the content-type
     * @param outputGenerator a function that will be given a {@link ISocketWrapper}, providing the
     *                        ability to send bytes on the socket.
     */
    public static IResponse buildStreamingResponse(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders, ThrowingConsumer<ISocketWrapper> outputGenerator) {
        return buildStreamingResponse(statusCode, extraHeaders.entrySet(), outputGenerator);
    }

    /**
     * Similar to {@link Response#buildStreamingResponse(StatusLine.StatusCode, Collection, ThrowingConsumer)} but here we know
     * the body length, so that will be sent to the client as content-length.
     * @param extraHeaders any extra headers for the response, such as the content-type
     * @param outputGenerator a function that will be given a {@link ISocketWrapper}, providing the
     *                        ability to send bytes on the socket.
     * @param bodyLength the length, in bytes, of the data to be sent to the client
     */
    public static IResponse buildStreamingResponse(StatusLine.StatusCode statusCode, Collection<Map.Entry<String, String>> extraHeaders, ThrowingConsumer<ISocketWrapper> outputGenerator, long bodyLength) {
        return new Response(statusCode, extraHeaders, null, outputGenerator, bodyLength, false);
    }

    /**
     * Similar to {@link Response#buildStreamingResponse(StatusLine.StatusCode, Map, ThrowingConsumer)} but here we know
     * the body length, so that will be sent to the client as content-length.
     * @param extraHeaders any extra headers for the response, such as the content-type
     * @param outputGenerator a function that will be given a {@link ISocketWrapper}, providing the
     *                        ability to send bytes on the socket.
     * @param bodyLength the length, in bytes, of the data to be sent to the client
     */
    public static IResponse buildStreamingResponse(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders, ThrowingConsumer<ISocketWrapper> outputGenerator, long bodyLength) {
        return buildStreamingResponse(statusCode, extraHeaders.entrySet(), outputGenerator, bodyLength);
    }

    /**
     * A constructor for situations where the developer wishes to send a small (less than a megabyte) byte array
     * to the client.  If there is need to send something of larger size, choose one these
     * alternate constructors:
     * FileChannel - for sending a large file: {@link Response#buildLargeFileResponse(Collection, String, Headers)}
     * Streaming - for more custom streaming control with a known body size: {@link Response#buildStreamingResponse(StatusLine.StatusCode, Map, ThrowingConsumer, long)}
     * Streaming - for more custom streaming control with body size unknown: {@link Response#buildStreamingResponse(StatusLine.StatusCode, Map, ThrowingConsumer)}
     *
     * @param extraHeaders any extra headers for the response, such as the content-type.
     */
    public static IResponse buildResponse(StatusLine.StatusCode statusCode, Collection<Map.Entry<String, String>> extraHeaders, byte[] body) {
        return new Response(statusCode, extraHeaders, body, socketWrapper -> sendByteArrayResponse(socketWrapper, body), body.length, false);
    }

    /**
     * A constructor for situations where the developer wishes to send a small (less than a megabyte) byte array
     * to the client.  If there is need to send something of larger size, choose one these
     * alternate constructors:
     * FileChannel - for sending a large file: {@link Response#buildLargeFileResponse(Map, String, Headers)}
     * Streaming - for more custom streaming control with a known body size: {@link Response#buildStreamingResponse(StatusLine.StatusCode, Map, ThrowingConsumer, long)}
     * Streaming - for more custom streaming control with body size unknown: {@link Response#buildStreamingResponse(StatusLine.StatusCode, Map, ThrowingConsumer)}
     *
     * @param extraHeaders any extra headers for the response, such as the content-type.
     */
    public static IResponse buildResponse(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders, byte[] body) {
        return buildResponse(statusCode, extraHeaders.entrySet(), body);
    }

    /**
     * Build an ordinary response, with a known body
     * @param extraHeaders extra HTTP headers, like <pre>content-type: text/html</pre>
     */
    public static IResponse buildResponse(StatusLine.StatusCode statusCode, Collection<Map.Entry<String, String>> extraHeaders, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new Response(statusCode, extraHeaders, bytes, socketWrapper -> sendByteArrayResponse(socketWrapper, bytes), bytes.length, true);
    }

    /**
     * Build an ordinary response, with a known body
     * @param extraHeaders extra HTTP headers, like <pre>content-type: text/html</pre>
     */
    public static IResponse buildResponse(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders, String body) {
        return buildResponse(statusCode, extraHeaders.entrySet(), body);
    }

    /**
     * This will send a large file to the client as a stream.
     * <em>Note that this does not check that the requested file is within a directory. If you expect the
     * path value to be untrusted - that is, set by a user - use {@link #buildLargeFileResponse(Collection, String, String, Headers)}</em>
     * @param extraHeaders extra headers you would like in the response, as key-value pairs in a map - for
     *                     example "Content-Type: video/mp4" or "Content-Type: application/zip"
     * @param filePath the string value of the path to this file. <em>Caution! if this is set by the user.  See notes above</em>
     * @param requestHeaders these are the headers from the request, which is needed here to set proper
     *                       response headers in some cases.
     */
    public static IResponse buildLargeFileResponse(Collection<Map.Entry<String, String>> extraHeaders, String filePath, Headers requestHeaders) throws IOException {
        Collection<Map.Entry<String, String>> adjustedHeaders = new ArrayList<>(extraHeaders);
        long fileSize = Files.size(Path.of(filePath));
        var range = new Range(requestHeaders, fileSize);
        StatusLine.StatusCode responseCode = CODE_200_OK;
        long length = fileSize;
        if (range.hasRangeHeader()) {
            long offset = range.getOffset();
            length = range.getLength();
            var lastIndex = (offset + length) - 1;
            adjustedHeaders.add(Map.entry("Content-Range", String.format("bytes %d-%d/%d", offset, lastIndex, fileSize)));
            responseCode = CODE_206_PARTIAL_CONTENT;
        }

        ThrowingConsumer<ISocketWrapper> outputGenerator = socketWrapper -> {
            try (RandomAccessFile reader = new RandomAccessFile(filePath, "r")) {
                reader.seek(range.getOffset());
                var fileChannel = reader.getChannel();
                sendFileChannelResponse(socketWrapper, fileChannel, range.getLength());
            }
        };

        return new Response(responseCode, adjustedHeaders, null, outputGenerator, length, false);
    }

    /**
     * This will send a large file to the client as a stream.
     * <em>Note that this does not check that the requested file is within a directory. If you expect the
     * path value to be untrusted - that is, set by a user - use {@link #buildLargeFileResponse(Map, String, String, Headers)}</em>
     * @param extraHeaders extra headers you would like in the response, as key-value pairs in a map - for
     *                     example "Content-Type: video/mp4" or "Content-Type: application/zip"
     * @param filePath the string value of the path to this file. <em>Caution! if this is set by the user.  See notes above</em>
     * @param requestHeaders these are the headers from the request, which is needed here to set proper
     *                       response headers in some cases.
     */
    public static IResponse buildLargeFileResponse(Map<String, String> extraHeaders, String filePath, Headers requestHeaders) throws IOException {
        return buildLargeFileResponse(extraHeaders.entrySet(), filePath, requestHeaders);
    }

    /**
     * This will send a large file to the client as a stream.
     * This is a safe version of the method, for when the file path is set by a user, meaning it is untrusted.
     * By setting a parentDirectory, the system will ensure that the requested path is within that directory, thus
     * disallowing path strings that could escape it, like prefixing with a slash.
     * @param extraHeaders extra headers you would like in the response, as key-value pairs in a map - for
     *                     example "Content-Type: video/mp4" or "Content-Type: application/zip"
     * @param filePath the string value of the path to this file. <em>Caution! if this is set by the user.  See notes above</em>
     * @param requestHeaders these are the headers from the request, which is needed here to set proper
     *                       response headers in some cases.
     * @param parentDirectory this value is presumed to be set by the developer, and thus isn't checked for safety. This
     *                        allows the developer to use directories anywhere in the system.
     */
    public static IResponse buildLargeFileResponse(Collection<Map.Entry<String, String>> extraHeaders, String filePath, String parentDirectory, Headers requestHeaders) throws IOException {
        Path path = FileUtils.safeResolve(parentDirectory, filePath);
        return buildLargeFileResponse(extraHeaders, path.toString(), requestHeaders);
    }

    /**
     * This will send a large file to the client as a stream.
     * This is a safe version of the method, for when the file path is set by a user, meaning it is untrusted.
     * By setting a parentDirectory, the system will ensure that the requested path is within that directory, thus
     * disallowing path strings that could escape it, like prefixing with a slash.
     * @param extraHeaders extra headers you would like in the response, as key-value pairs in a map - for
     *                     example "Content-Type: video/mp4" or "Content-Type: application/zip"
     * @param filePath the string value of the path to this file. <em>Caution! if this is set by the user.  See notes above</em>
     * @param requestHeaders these are the headers from the request, which is needed here to set proper
     *                       response headers in some cases.
     * @param parentDirectory this value is presumed to be set by the developer, and thus isn't checked for safety. This
     *                        allows the developer to use directories anywhere in the system.
     */
    public static IResponse buildLargeFileResponse(Map<String, String> extraHeaders, String filePath, String parentDirectory, Headers requestHeaders) throws IOException {
        return  buildLargeFileResponse(extraHeaders.entrySet(), filePath, parentDirectory, requestHeaders);
    }

    /**
     * Build a {@link Response} with just a status code and headers, without a body
     * @param extraHeaders extra HTTP headers
     */
    public static IResponse buildLeanResponse(StatusLine.StatusCode statusCode, Collection<Map.Entry<String, String>> extraHeaders) {
        return new Response(statusCode, extraHeaders, null, socketWrapper -> {}, 0, false);
    }

    /**
     * Build a {@link Response} with just a status code and headers, without a body
     * @param extraHeaders extra HTTP headers
     */
    public static IResponse buildLeanResponse(StatusLine.StatusCode statusCode, Map<String, String> extraHeaders) {
        return buildLeanResponse(statusCode, extraHeaders.entrySet());
    }

    /**
     * Build a {@link Response} with only a status code, with no body and no extra headers.
     */
    public static IResponse buildLeanResponse(StatusLine.StatusCode statusCode) {
        return new Response(statusCode, List.of(), null, socketWrapper -> {}, 0, false);
    }

    /**
     * A helper method to create a response that returns a
     * 303 status code ("see other").  Provide a url that will
     * be handed to the browser.  This url may be relative or absolute.
     */
    public static IResponse redirectTo(String locationUrl) {
        try {
            // check if we can create a URL from this - any failures indicate invalid syntax.
            URI.create(locationUrl);
        } catch (Exception ex) {
            throw new WebServerException("Failure in redirect to (" + locationUrl + "). Exception: " + ex);
        }
        return buildResponse(StatusLine.StatusCode.CODE_303_SEE_OTHER, Map.of("location", locationUrl, "Content-Type", "text/html; charset=UTF-8"), "<p>See <a href=\""+locationUrl+"\">this link</a></p>");
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate. This version of the helper
     * lets you add extra headers on top of the basic content-type headers
     * that are needed to specify this is HTML.
     */
    public static IResponse htmlOk(String body, Collection<Map.Entry<String, String>> extraHeaders) {
        var headers = new HashMap<String, String>();
        headers.put("Content-Type", "text/html; charset=UTF-8");
        for (Map.Entry<String, String> entry : extraHeaders) {
            headers.put(entry.getKey(), entry.getValue());
        }
        return buildResponse(StatusLine.StatusCode.CODE_200_OK, headers.entrySet(), body);
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate. This version of the helper
     * lets you add extra headers on top of the basic content-type headers
     * that are needed to specify this is HTML.
     */
    public static IResponse htmlOk(String body, Map<String, String> extraHeaders) {
        return htmlOk(body, extraHeaders.entrySet());
    }

    /**
     * If you are returning HTML text with a 200 ok, this is a helper that
     * lets you skip some of the boilerplate.
     */
    public static IResponse htmlOk(String body) {
        return htmlOk(body, Map.of());
    }

    @Override
    public Collection<Map.Entry<String, String>> getExtraHeaders() {
        return extraHeaders;
    }

    @Override
    public StatusLine.StatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public boolean isBodyText() {
        return isBodyText;
    }

    @Override
    public long getBodyLength() {
        if (body != null) {
            return body.length;
        } else {
            return bodyLength;
        }
    }

    @Override
    public void sendBody(ISocketWrapper sw) throws IOException {
        try {
            outputGenerator.accept(sw);
        } catch (Exception ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    /**
     * put bytes from a file into the socket, sending to the client
     * @param fileChannel the file we are reading from, based on a {@link RandomAccessFile}
     * @param length the number of bytes to send.  May be less than the full length of this {@link FileChannel}
     */
    private static void sendFileChannelResponse(ISocketWrapper sw, FileChannel fileChannel, long length) throws IOException {
        try {
            int bufferSize = 8 * 1024;
            ByteBuffer buff = ByteBuffer.allocate(bufferSize);
            long countBytesLeftToSend = length;
            while (true) {
                int countBytesRead = fileChannel.read(buff);
                if (countBytesRead <= 0) {
                    break;
                } else {
                    if (countBytesLeftToSend < countBytesRead) {
                        sw.send(buff.array(), 0, (int)countBytesLeftToSend);
                        break;
                    } else {
                        sw.send(buff.array(), 0, countBytesRead);
                    }
                    buff.clear();
                }
                countBytesLeftToSend -= countBytesRead;
            }
        } finally {
            fileChannel.close();
        }
    }

    private static void sendByteArrayResponse(ISocketWrapper sw, byte[] body) throws IOException {
        sw.send(body);
    }

    @Override
    public byte[] getBody() {
        return body;
    }

    /**
     * Compress the data in this body using gzip.
     * <br>
     * This operates by getting the body field from this instance of {@link Response} and
     * creating a new Response with the compressed data.
     */
    Response compressBody() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var gos = new GZIPOutputStream(out);
        gos.write(body);
        gos.finish();
        return (Response)Response.buildResponse(
                statusCode,
                extraHeaders,
                out.toByteArray()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Response response = (Response) o;
        return bodyLength == response.bodyLength && isBodyText == response.isBodyText && statusCode == response.statusCode && Objects.equals(extraHeaders, response.extraHeaders) && Arrays.equals(body, response.body);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(statusCode, extraHeaders, bodyLength, isBodyText);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "Response{" +
                "statusCode=" + statusCode +
                ", extraHeaders=" + extraHeaders +
                ", bodyLength=" + bodyLength +
                ", isBodyText=" + isBodyText +
                '}';
    }
}
