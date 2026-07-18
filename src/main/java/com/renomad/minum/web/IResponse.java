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
    Headers getExtraHeaders();

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
     * @throws Exception so that when this is called in {@link WebFramework#httpProcessing(ISocketWrapper)},
     * the exception type can be examined and some {@link java.io.IOException} types
     * like {@link java.net.SocketException} can be recorded more quietly, instead of
     * rendering a whole stack trace in the logs.
     */
    void sendBody(ISocketWrapper sw) throws Exception;

    /**
     * Returns the bytes of the Response body being sent to the client
     */
    byte[] getBody();

    /**
     * The "OutputGenerator" is the code run to put data onto
     * the socket.  A commonly provided output generator is
     * a lambda like this: `socketWrapper -> socketWrapper.send(body)`
     * <br>
     * However, in some cases it is needed to provide greater sophistication.
     * For example, see the output generator in {@link Response#buildLargeFileResponse},
     * which looks like this:
     * <pre>
     * {@code
     *         ThrowingConsumer<ISocketWrapper> outputGenerator = socketWrapper -> {
     *             try (RandomAccessFile reader = new RandomAccessFile(filePath, "r")) {
     *                 reader.seek(range.getOffset());
     *                 var fileChannel = reader.getChannel();
     *                 sendFileChannelResponse(socketWrapper, fileChannel, range.getLength());
     *             }
     *         };
     * }
     * </pre>
     */
    ThrowingConsumer<ISocketWrapper> getOutputGenerator();
}
