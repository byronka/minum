package com.renomad.minum.web;

import com.renomad.minum.state.Constants;
import com.renomad.minum.htmlparsing.HtmlParseNode;
import com.renomad.minum.htmlparsing.HtmlParser;
import com.renomad.minum.htmlparsing.TagName;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.utils.StacktraceUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tools to enable system-wide integration testing
 */
public final class FunctionalTesting {

    private final String host;
    private final int port;
    private final IInputStreamUtils inputStreamUtils;
    private final ILogger logger;
    private final Constants constants;
    private final IBodyProcessor bodyProcessor;

    /**
     * Allows the user to set the host and port to target
     * for testing.
     */
    public FunctionalTesting(Context context, String host, int port) {
        this.constants = context.getConstants();
        this.host = host;
        this.port = port;

        this.inputStreamUtils = new InputStreamUtils(constants.maxReadLineSizeBytes);
        this.logger = context.getLogger();
        bodyProcessor = new BodyProcessor(context);
    }

    /**
     * A {@link Response} designed to work with {@link FunctionalTesting}
     */
    public record TestResponse(StatusLine statusLine, Headers headers, Body body) {

        public static final TestResponse EMPTY = new TestResponse(StatusLine.EMPTY, Headers.EMPTY, Body.EMPTY);

        /**
         * Presuming the response body is HTML, search for a single
         * HTML element with the given tag name and attributes.
         *
         * @return {@link HtmlParseNode#EMPTY} if none found, a particular node if found,
         * and an exception thrown if more than one found.
         */
        public HtmlParseNode searchOne(TagName tagName, Map<String, String> attributes) {
            var htmlParser = new HtmlParser();
            var nodes = htmlParser.parse(body.asString());
            var searchResults = htmlParser.search(nodes, tagName, attributes);
            if (searchResults.size() > 1) {
                throw new InvariantException("More than 1 node found.  Here they are:" + searchResults);
            }
            if (searchResults.isEmpty()) {
                return HtmlParseNode.EMPTY;
            } else {
                return searchResults.getFirst();
            }
        }

        /**
         * Presuming the response body is HTML, search for all
         * HTML elements with the given tag name and attributes.
         *
         * @return a list of however many elements matched
         */
        public List<HtmlParseNode> search(TagName tagName, Map<String, String> attributes) {
            var htmlParser = new HtmlParser();
            var nodes = htmlParser.parse(body.asString());
            return htmlParser.search(nodes, tagName, attributes);
        }
    }

    /**
     * Send a GET request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)}
     *             for pathname
     */
    public TestResponse get(String path) {
        ArrayList<String> headers = new ArrayList<>();
        return send(RequestLine.Method.GET, path, new byte[0], headers);
    }

    /**
     * Send a GET request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)}
     *             for pathname
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse get(String path, List<String> extraHeaders) {
        return send(RequestLine.Method.GET, path, new byte[0], extraHeaders);
    }

    /**
     * Send a POST request (as a client to the server) using url encoding
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)}
     *             for pathname
     * @param payload a body payload in string form
     */
    public TestResponse post(String path, String payload) {
        ArrayList<String> headers = new ArrayList<>();
        return post(path, payload, headers);
    }

    /**
     * Send a POST request (as a client to the server) using url encoding
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)}
     *             for pathname
     * @param payload a body payload in string form
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse post(String path, String payload, List<String> extraHeaders) {
        var headers = new ArrayList<String>();
        headers.add("Content-Type: application/x-www-form-urlencoded");
        headers.addAll(extraHeaders);
        return send(RequestLine.Method.POST, path, payload.getBytes(StandardCharsets.UTF_8), headers);
    }

    /**
     * Send a request as a client to the server
     * <p>
     *     This helper method is the same as {@link #send(RequestLine.Method, String, byte[], List)} except
     *     it will set the body as empty and does not require any extra headers to be set. In this
     *     case, the headers sent are very minimal.  See the source.
     * </p>
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)}
     *             for pathname
     */
    public TestResponse send(RequestLine.Method method, String path) {
        return send(method, path, new byte[0], List.of());
    }

    /**
     * Send a request as a client to the server
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)}
     *             for pathname
     * @param payload a body payload in byte array form
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     * @return a properly-built client, or null if exceptions occur
     */
    public TestResponse send(RequestLine.Method method, String path, byte[] payload, List<String> extraHeaders) {
        try (Socket socket = new Socket(host, port)) {
            try (ISocketWrapper client = startClient(socket)) {
                return innerClientSend(client, method, path, payload, extraHeaders);
            }
        } catch (Exception e) {
            logger.logDebug(() -> "Error during client send: " + StacktraceUtils.stackTraceToString(e));
            return TestResponse.EMPTY;
        }
    }

    /**
     * Create a client {@link ISocketWrapper} connected to the running host server
     */
    ISocketWrapper startClient(Socket socket) throws IOException {
        logger.logDebug(() -> String.format("Just created new client socket: %s", socket));
        return new SocketWrapper(socket, null, logger, constants.socketTimeoutMillis, constants.hostName);
    }

    public TestResponse innerClientSend(
            ISocketWrapper client,
            RequestLine.Method method,
            String path,
            byte[] payload,
            List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY;

        InputStream is = client.getInputStream();

        client.sendHttpLine(method + " /" + path + " HTTP/1.1");
        client.sendHttpLine(String.format("Host: %s:%d", host, port));
        client.sendHttpLine("Content-Length: " + payload.length);
        for (String header : extraHeaders) {
            client.sendHttpLine(header);
        }
        client.sendHttpLine("");
        client.send(payload);

        StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
        List<String> allHeaders = Headers.getAllHeaders(is, inputStreamUtils);
        Headers headers = new Headers(allHeaders);


        if (WebFramework.isThereIsABody(headers) && method != RequestLine.Method.HEAD) {
            logger.logTrace(() -> "There is a body. Content-type is " + headers.contentType());
            body = bodyProcessor.extractData(is, headers);
        }
        return new TestResponse(statusLine, headers, body);
    }


}
