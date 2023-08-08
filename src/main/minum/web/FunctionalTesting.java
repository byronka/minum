package minum.web;

import minum.Context;
import minum.htmlparsing.HtmlParseNode;
import minum.htmlparsing.HtmlParser;
import minum.htmlparsing.TagName;
import minum.logging.ILogger;
import minum.utils.MyThread;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tools to enable system-wide integration testing
 */
public class FunctionalTesting {

    private final Context context;
    private final WebEngine webEngine;
    private final Server primaryServer;
    private final InputStreamUtils inputStreamUtils;
    private final WebFramework webFramework;
    private final ILogger logger;

    public FunctionalTesting(Context context) {
        this.context = context;
        this.webEngine = context.getFullSystem().getWebEngine();
        this.primaryServer = context.getFullSystem().getServer();
        this.inputStreamUtils = new InputStreamUtils(context);
        this.webFramework = context.getFullSystem().getWebFramework();
        this.logger = context.getLogger();
    }

    /**
     * A {@link Response} designed to work with {@link FunctionalTesting}
     */
    public record TestResponse(StatusLine statusLine, Headers headers, Body body) {

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
            return htmlParser.searchOne(nodes, tagName, attributes);
        }
    }

    /**
     * Send a GET request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     */
    public TestResponse get(String path) throws IOException {
        return get(path, List.of());
    }

    /**
     * Send a GET request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse get(String path, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY(context);
        Headers headers = null;
        StatusLine statusLine = null;
        try (var client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a GET request
            client.sendHttpLine("GET /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");

            statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

            headers = Headers.make(context, inputStreamUtils).extractHeaderInformation(is);

            BodyProcessor bodyProcessor = new BodyProcessor(context);


            // Determine whether there is a body (a block of data) in this request
            if (webFramework.isThereIsABody(headers)) {
                Headers finalHeaders = headers;
                logger.logTrace(() -> "There is a body. Content-type is " + finalHeaders.contentType());
                body = bodyProcessor.extractData(is, headers);
            }

        } catch (SocketException ex) {
            if (ex.getMessage().equals("An established connection was aborted by the software in your host machine")) {
                /*
                When the server is done communicating with us, it will close
                the socket on its side, which will cause a SocketException on our side.
                If the message matches what we have here, it's just that happening, no
                need to pass it up further, it's expected and ok.
                 */
            } else {
                throw ex;
            }
        }
        MyThread.sleep(100);
        return new TestResponse(statusLine, headers, body);
    }

    /**
     * Send a POST request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param payload a body payload in string form
     */
    public TestResponse post(String path, String payload) throws IOException {
        return post(path, payload, List.of());
    }

    /**
     * Send a POST request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param payload a body payload in string form
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse post(String path, String payload, List<String> extraHeaders) throws IOException {
        return post(path, payload.getBytes(StandardCharsets.UTF_8), extraHeaders);
    }

    /**
     * Send a POST request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param payload a body payload in byte array form
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse post(String path, byte[] payload, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY(context);
        Headers headers = null;
        StatusLine statusLine = null;
        try (var client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a POST request
            client.sendHttpLine("POST /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            client.sendHttpLine("Content-Length: " + payload.length);
            client.sendHttpLine("Content-Type: application/x-www-form-urlencoded");
            for (String header : extraHeaders) {
                client.sendHttpLine(header);
            }
            client.sendHttpLine("");
            client.send(payload);

            statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

            headers = Headers.make(context, inputStreamUtils).extractHeaderInformation(is);

            BodyProcessor bodyProcessor = new BodyProcessor(context);


            if (webFramework.isThereIsABody(headers)) {
                Headers finalHeaders = headers;
                logger.logTrace(() -> "There is a body. Content-type is " + finalHeaders.contentType());
                body = bodyProcessor.extractData(is, headers);
            }

        } catch (SocketException ex) {
            if (ex.getMessage().equals("An established connection was aborted by the software in your host machine")) {
                /*
                When the server is done communicating with us, it will close
                the socket on its side, which will cause a SocketException on our side.
                If the message matches what we have here, it's just that happening, no
                need to pass it up further, it's expected and ok.
                 */
            } else {
                throw ex;
            }
        }
        MyThread.sleep(100);
        return new TestResponse(statusLine, headers, body);
    }

}
