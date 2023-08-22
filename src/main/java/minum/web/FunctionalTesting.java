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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static minum.utils.Invariants.mustBeTrue;

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
            var searchResults = htmlParser.search(nodes, tagName, attributes);
            mustBeTrue(searchResults.size() == 0 || searchResults.size() == 1, "More than 1 node found.  Here they are:" + searchResults);
            if (searchResults.size() == 0) {
                return HtmlParseNode.EMPTY;
            } else {
                return searchResults.get(0);
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
            var searchResults = htmlParser.search(nodes, tagName, attributes);
            return searchResults;
        }
    }

    /**
     * Send a GET request (as a client to the server)
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     */
    public TestResponse get(String path) throws IOException {
        ArrayList<String> headers = new ArrayList<>();
        return send(StartLine.Verb.GET, path, new byte[0], headers);
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
        return send(StartLine.Verb.GET, path, new byte[0], extraHeaders);
    }

    /**
     * Send a POST request (as a client to the server) using url encoding
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param payload a body payload in string form
     */
    public TestResponse post(String path, String payload) throws IOException {
        ArrayList<String> headers = new ArrayList<>();
        return post(path, payload, headers);
    }

    /**
     * Send a POST request (as a client to the server) using url encoding
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param payload a body payload in string form
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse post(String path, String payload, List<String> extraHeaders) throws IOException {
        var headers = new ArrayList<String>();
        headers.add("Content-Type: application/x-www-form-urlencoded");
        headers.addAll(extraHeaders);
        return send(StartLine.Verb.POST, path, payload.getBytes(StandardCharsets.UTF_8), headers);
    }

    /**
     * Send a request as a client to the server
     *
     * <p>
     *     This helper method is the same as {@link #send(StartLine.Verb, String, byte[], List)} except
     *     that it will automatically set the body as empty
     * </p>
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse send(StartLine.Verb verb, String path, List<String> extraHeaders) throws IOException {
        return send(verb, path, new byte[0], extraHeaders);
    }

    /**
     * Send a request as a client to the server
     * <p>
     *     This helper method is the same as {@link #send(StartLine.Verb, String, byte[], List)} except
     *     it will set the body as empty and does not require any extra headers to be set. In this
     *     case, the headers sent are very minimal.  See the source.
     * </p>
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     */
    public TestResponse send(StartLine.Verb verb, String path) throws IOException {
        return send(verb, path, new byte[0], List.of());
    }

    /**
     * Send a request as a client to the server
     * @param path the path to an endpoint, that is, the value for path
     *            that is entered in {@link WebFramework#registerPath(StartLine.Verb, String, Function)}
     *             for pathname
     * @param payload a body payload in byte array form
     * @param extraHeaders a list containing extra headers you need the client to send, for
     *                     example, <pre>{@code List.of("cookie: id=foo")}</pre>
     */
    public TestResponse send(StartLine.Verb verb, String path, byte[] payload, List<String> extraHeaders) throws IOException {
        Body body = Body.EMPTY(context);
        Headers headers = null;
        StatusLine statusLine = null;
        try (var client = webEngine.startClient(primaryServer)) {
            InputStream is = client.getInputStream();

            // send a POST request
            client.sendHttpLine(verb + " /"+path+" HTTP/1.1");
            client.sendHttpLine("Host: localhost:8080");
            client.sendHttpLine("Content-Length: " + payload.length);
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
            if (ex.getMessage().equals("An established connection was aborted by the software in your host machine") ||
                    ex.getMessage().equals("Connection reset")) {
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

        return new TestResponse(statusLine, headers, body);
    }

}
