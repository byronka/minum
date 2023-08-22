package minum.web;

import minum.Context;
import minum.exceptions.ForbiddenUseException;
import minum.htmlparsing.ParsingException;
import minum.logging.TestLogger;
import minum.utils.InvariantException;
import minum.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;

import static minum.testing.TestFramework.*;
import static minum.web.StartLine.Verb.GET;
import static minum.web.StartLine.Verb.POST;
import static minum.web.StartLine.startLineRegex;
import static minum.web.StatusLine.StatusCode._200_OK;
import static minum.web.StatusLine.StatusCode._404_NOT_FOUND;
import static minum.web.HttpVersion.ONE_DOT_ONE;

public class WebTests {
    static final ZonedDateTime default_zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));
    private final ExecutorService es;
    private final InputStreamUtils inputStreamUtils;
    private final Context context;
    private final TestLogger logger;

    public WebTests(Context context) {
        this.context = context;
        this.logger = (TestLogger) context.getLogger();
        this.es = context.getExecutorService();
        this.inputStreamUtils = new InputStreamUtils(context);
        logger.testSuite("WebTests");
    }

    /*$      /$$           /$$               /$$                           /$$
    | $$  /$ | $$          | $$              | $$                          | $$
    | $$ /$$$| $$  /$$$$$$ | $$$$$$$        /$$$$$$    /$$$$$$   /$$$$$$$ /$$$$$$   /$$$$$$$
    | $$/$$ $$ $$ /$$__  $$| $$__  $$      |_  $$_/   /$$__  $$ /$$_____/|_  $$_/  /$$_____/
    | $$$$_  $$$$| $$$$$$$$| $$  \ $$        | $$    | $$$$$$$$|  $$$$$$   | $$   |  $$$$$$
    | $$$/ \  $$$| $$_____/| $$  | $$        | $$ /$$| $$_____/ \____  $$  | $$ /$$\____  $$
    | $$/   \  $$|  $$$$$$$| $$$$$$$/        |  $$$$/|  $$$$$$$ /$$$$$$$/  |  $$$$//$$$$$$$/
    |__/     \__/ \_______/|_______/          \___/   \_______/|_______/    \___/ |______*/
    public void tests() throws IOException {

        WebEngine webEngine = new WebEngine(context);

        logger.test("client / server");{
            try (var primaryServer = webEngine.startServer(es)) {
                try (var client = webEngine.startClient(primaryServer)) {
                    try (var server = primaryServer.getServer(client)) {
                        InputStream is = server.getInputStream();

                        client.send("hello foo!\n");
                        String result = inputStreamUtils.readLine(is);
                        assertEquals("hello foo!", result);
                    }
                }
            }
        }

        logger.test("client / server with more conversation");{
            String msg1 = "hello foo!";
            String msg2 = "and how are you?";
            String msg3 = "oh, fine";

            try (var primaryServer = webEngine.startServer(es)) {
                try (var client = webEngine.startClient(primaryServer)) {
                    try (var server = primaryServer.getServer(client)) {
                        InputStream sis = server.getInputStream();
                        InputStream cis = client.getInputStream();

                        // client sends, server receives
                        client.sendHttpLine(msg1);
                        assertEquals(msg1, inputStreamUtils.readLine(sis));

                        // server sends, client receives
                        server.sendHttpLine(msg2);
                        assertEquals(msg2, inputStreamUtils.readLine(cis));

                        // client sends, server receives
                        client.sendHttpLine(msg3);
                        assertEquals(msg3, inputStreamUtils.readLine(sis));
                    }
                }
            }
        }

        logger.test("What happens if we throw an exception in a thread");{
            es.submit(() -> {
                try {
                    throw new RuntimeException("No worries folks, just testing the exception handling, from test \"What happens if we throw an exception in a thread\"");
                } catch (Exception ex) {
                    logger.logDebug(ex::getMessage);
                }
            });
        }

        logger.test("like we're a minum.web server");{
            try (var primaryServer = webEngine.startServer(es)) {
                try (var client = webEngine.startClient(primaryServer)) {
                    try (var server = primaryServer.getServer(client)) {
                        // send a GET request
                        client.sendHttpLine("GET /index.html HTTP/1.1");
                        client.sendHttpLine("cookie: abc=123");
                        client.sendHttpLine("");
                        server.sendHttpLine("HTTP/1.1 200 OK");
                    }
                }
            }
        }

        /*
          If we provide some code to handle things on the server
          side when it accepts a connection, then it will more
          truly act like the minum.web server we want it to be.
         */
        logger.test("starting server with a handler");{
          /*
            Simplistic proof-of-concept of the minum.primary server
            handler.  The socket has been created and as new
            clients call it, this method handles each request.

            There's nothing to prevent us using this as the entire
            basis of a minum.web minum.testing.
           */
            ThrowingConsumer<ISocketWrapper, IOException> handler = (sw) -> {
                InputStream is = sw.getInputStream();
                logger.logDebug(() -> inputStreamUtils.readLine(is));
            };

            try (Server primaryServer = webEngine.startServer(es, handler)) {
                try (var client = webEngine.startClient(primaryServer)) {
                    // send a GET request
                    client.sendHttpLine("GET /index.html HTTP/1.1");

                }
            }
        }

        /*
         * This class belongs to the test below, "starting server with a handler part 2"
         *
         * This represents the shape an endpoint could take. It's given a request object,
         * we presume it has everything is needs to do its work (query strings on the path,
         * header values, body contents, all that stuff), and it replies with a response
         * object that gets ferried along to the client.
         */
        class Summation {
            static Response addTwoNumbers(Request r) {
                int aValue = Integer.parseInt(r.startLine().queryString().get("a"));
                int bValue = Integer.parseInt(r.startLine().getPathDetails().queryString().get("b"));
                int sum = aValue + bValue;
                String sumString = String.valueOf(sum);
                return Response.htmlOk(sumString);
            }
        }

        logger.test("starting server with a handler part 2");{
            var wf = new WebFramework(context, default_zdt);
            wf.registerPath(GET, "add_two_numbers", Summation::addTwoNumbers);
            try (Server primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
                try (var client = webEngine.startClient(primaryServer)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /add_two_numbers?a=42&b=44 HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

                    assertEquals(statusLine.rawValue(), "HTTP/1.1 200 OK");

                    Headers hi = Headers.make(context, inputStreamUtils).extractHeaderInformation(is);

                    assertEquals(hi.valueByKey("server"), List.of("minum"));
                    assertTrue(hi.valueByKey("date") != null);
                    assertEquals(hi.valueByKey("content-type"), List.of("text/html; charset=UTF-8"));
                    assertEquals(hi.valueByKey("content-length"), List.of("2"));

                    String body = readBody(is, hi.contentLength());

                    assertEquals(body, "86");
                }
            }
        }

        // test while controlling the fake socket wrapper
        logger.test("TDD of a handler");{
            FakeSocketWrapper sw = new FakeSocketWrapper();
            AtomicReference<String> result = new AtomicReference<>();
            sw.sendHttpLineAction = result::set;

            // this is what we're really going to test
            ThrowingConsumer<ISocketWrapper, IOException> handler = (socketWrapper) -> socketWrapper.sendHttpLine("this is a test");
            handler.accept(sw);

            assertEquals("this is a test", result.get());
        }

        // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line
        // 1. the method (GET, POST, etc.)
        // 2. the request target
        // 3. the HTTP version (e.g. HTTP/1.1)
        logger.test("We should be able to pull valuable information from the start line");{
            Matcher m = startLineRegex.matcher("GET /index.html HTTP/1.1");
            assertTrue(m.matches());
        }

        logger.test("alternate case for extractStartLine - POST");{
            StartLine sl = StartLine.EMPTY(context).extractStartLine("POST /something HTTP/1.0");
            assertEquals(sl.getVerb(), POST);
        }

        logger.test("alernate case - empty path");{
            StartLine sl = StartLine.EMPTY(context).extractStartLine("GET / HTTP/1.1");
            assertEquals(sl.getVerb(), GET);
            assertEquals(sl.getPathDetails().isolatedPath(), "");
        }

        logger.test("negative cases for extractStartLine");{
            // missing verb
            List<String> badStartLines = List.of(
                    "/something HTTP/1.1",
                    "GET HTTP/1.1",
                    "GET /something",
                    "GET /something HTTP/1.2",
                    "GET /something HTTP/",
                    ""
            );
            for (String s : badStartLines) {
                assertEquals(StartLine.EMPTY(context).extractStartLine(s), StartLine.EMPTY(context));
            }
            assertThrows(InvariantException.class, () -> StartLine.EMPTY(context).extractStartLine(null));
        }

        logger.test("positive test for extractStatusLine");{
            StatusLine sl = StatusLine.extractStatusLine("HTTP/1.1 200 OK");
            assertEquals(sl.status(), _200_OK);
        }

        logger.test("negative tests for extractStatusLine");{
            // missing status description
            assertThrows(InvariantException.class, "HTTP/1.1 200 must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP/1.1 200"));
            // missing status code
            assertThrows(InvariantException.class, "HTTP/1.1  OK must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP/1.1  OK"));
            // missing HTTP version
            assertThrows(InvariantException.class, "HTTP 200 OK must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP 200 OK"));
            // invalid HTTP version
            assertThrows(InvariantException.class, "HTTP/1.3 200 OK must match the statusLinePattern: ^HTTP/(1.1|1.0) (\\d{3}) (.*)$", () -> StatusLine.extractStatusLine("HTTP/1.3 200 OK"));
            // invalid status code
            assertThrows(NoSuchElementException.class, "No value present", () -> StatusLine.extractStatusLine("HTTP/1.1 199 OK"));
        }

        /*
        as part of sending data to the server, we'll encode data like the following.  if we
        set value_a to 123 and value_b to 456, it looks like: value_a=123&value_b=456

        we want to convert that string to a map, like this: value_a -> 123, value_b -> 456
         */
        logger.test("parseUrlEncodedForm should properly parse data");{
            final var expected = Map.of("value_a", "123", "value_b", "456");
            final var result = new BodyProcessor(context).parseUrlEncodedForm("value_a=123&value_b=456");
            assertEquals(expected.get("value_a"), result.asString("value_a"));
            assertEquals(expected.get("value_b"), result.asString("value_b"));
        }

        logger.test("parseUrlEncodedForm edge cases"); {
            // blank key
            final var ex2 = assertThrows(ParsingException.class, () -> new BodyProcessor(context).parseUrlEncodedForm("=123"));
            assertEquals(ex2.getCause().getMessage(), "The key must not be blank");

            // duplicate keys
            final var ex3 = assertThrows(ParsingException.class, () -> new BodyProcessor(context).parseUrlEncodedForm("a=123&a=123"));
            assertEquals(ex3.getCause().getMessage(), "a was duplicated in the post body - had values of 123 and 123");

            // empty value
            final var result = new BodyProcessor(context).parseUrlEncodedForm("mykey=");
            assertEquals(result.asString("mykey"), "");

            // null value - value of %NULL%
            final var result2 = new BodyProcessor(context).parseUrlEncodedForm("mykey=%NULL%");
            assertEquals(result2.asString("mykey"), "");
        }

        logger.test("when we post data to an endpoint, it can extract the data"); {
            var wf = new WebFramework(context, default_zdt);
            try (var primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
                try (var client = webEngine.startClient(primaryServer)) {
                    wf.registerPath(
                            POST,
                            "some_post_endpoint",
                            request -> Response.htmlOk(request.body().asString("value_a"))
                    );

                    InputStream is = client.getInputStream();

                    final var postedData = "value_a=123&value_b=456";

                    // send a POST request
                    client.sendHttpLine("POST /some_post_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    final var contentLengthLine = "Content-Length: " + postedData.length();
                    client.sendHttpLine(contentLengthLine);
                    client.sendHttpLine("Content-Type: application/x-www-form-urlencoded");
                    client.sendHttpLine("");
                    client.sendHttpLine(postedData);

                    // the server will respond to us.  Check everything is legit.
                    StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    Headers hi = Headers.make(context, inputStreamUtils).extractHeaderInformation(client.getInputStream());
                    String body = readBody(is, hi.contentLength());

                    assertEquals(body, "123");
                }
            }
        }

        logger.test("when the requested endpoint does not exist, we get a 404 response"); {
            var wf = new WebFramework(context, default_zdt);
            try (var primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
                try (var client = webEngine.startClient(primaryServer)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine.rawValue(), "HTTP/1.1 404 NOT FOUND");
                }
            }
        }

        /*
        If a client is POSTing data to our server, there are two allowed ways of doing it
        in HTTP/1.1.  One is to include the content-length, and the other is to do
        transfer-encoding: chunked.  This test is to drive implementation of chunk encoding.
        see https://www.rfc-editor.org/rfc/rfc7230#section-3.3.3
         */
        logger.test("We should be able to handle transfer-encoding: chunked");{
            String receivedData =
                """
                HTTP/1.1 200 OK
                Content-Type: text/plain
                Transfer-Encoding: chunked
                \r
                4\r
                Wiki\r
                6\r
                pedia \r
                E\r
                in \r
                \r
                chunks.
                0\r
                \r
                """.stripLeading();

            // read through the headers to get to the sweet juicy body below
            InputStream inputStream = new ByteArrayInputStream(receivedData.getBytes(StandardCharsets.UTF_8));
            while(!inputStreamUtils.readLine(inputStream).isEmpty()){
                // do nothing, just going through the bytes of this stream
            }

            // and now, the magic of encoded chunks
            final var result = StringUtils.byteArrayToString(inputStreamUtils.readChunkedEncoding(inputStream));

            assertEquals(result, """
                Wikipedia in \r
                \r
                chunks.""".stripLeading());

        }

        logger.test("Looking into how to split a byte array using a string delimiter"); {
            byte[] multiPartData = makeTestMultiPartData();
            var bp = new BodyProcessor(context);

            List<byte[]> result = bp.split(multiPartData, "--i_am_a_boundary");
            assertEquals(result.size(), 2);
            assertEquals(result.get(0).length, 101);
            assertEquals(result.get(1).length, 129);
        }

        logger.test("Examining the algorithm for parsing multipart data"); {
            byte[] multiPartData = makeTestMultiPartData();
            var bp = new BodyProcessor(context);

            final var result = bp.parseMultiform(multiPartData, "i_am_a_boundary");
            assertEquals(result.asString("text1"), "I am a value that is text");
            assertEqualByteArray(result.asBytes("image_uploads"), new byte[]{1, 2, 3});
        }

        /*
         * There are two primary ways to send data in requests and responses.
         * 1. Url encoding - this is what we have been doing so far, converting
         *    some values to percent-encoded.
         * 2. Multipart - providing a boundary token of our choice, along with
         *    a couple headers to indicate critical information, then a blank line,
         *    then the raw content.
         *
         * This test will cause us to create a program that is able to read each
         * section of the multipart format.  The interesting/difficult problem
         * I'm anticipating is that we need to be able to read binary and ascii.
         *
         * See, we will only read until we encounter the boundary text, which might
         * be "abc123".  So if we read byte-by-byte, as soon as we hit a byte that
         * can be "a", we need to jump into the possibility that we may be looking
         * at the beginning of "abc123".
         */
        logger.test("we should be able to receive multipart form data"); {
            byte[] multiPartData = makeTestMultiPartData();

            // This is the core of the test - here's where we'll process receiving a multipart data

            final Function<StartLine, Function<Request, Response>> testHandler = (sl -> r -> {
                if (r.body().asString("text1").equals("I am a value that is text") &&
                        (r.body().asBytes("image_uploads"))[0] == 1 &&
                        (r.body().asBytes("image_uploads"))[1] == 2 &&
                        (r.body().asBytes("image_uploads"))[2] == 3
                ) {
                    return Response.htmlOk("<p>r was </p>");
                } else {
                  return new Response(_404_NOT_FOUND);
                }
            });

            WebFramework wf = new WebFramework(context, default_zdt);
            try (Server primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
                try (var client = webEngine.startClient(primaryServer)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("POST /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Content-Type: multipart/form-data; boundary=i_am_a_boundary");
                    client.sendHttpLine("Content-length: " + multiPartData.length);
                    client.sendHttpLine("");
                    client.send(multiPartData);

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine.status(), _200_OK);
                }
            }
        }

        logger.test("Headers test - multiple headers"); {
            Headers headers = new Headers(List.of("foo: a", "foo: b"), context);
            assertEqualsDisregardOrder(headers.valueByKey("foo"), List.of("a","b"));
        }

        /*
        If we just stayed with path plus query string....  But no, it's boring, I guess, to
        have consistency and safety, so people decided we should have paths
        with a pattern like /my/path/{id}.  unnecessary.  stupid.  whatever,
        it's the world I have to live in.  It's a bit complicated of a test, so I'll explain as I go.
         */
        logger.test("Matching a path in an insane world"); {
            // The startline causing us heartache
            String startLineString = "GET /.well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX HTTP/1.1";

            // Convert it to a typed value
            var startLine = StartLine.EMPTY(context).extractStartLine(startLineString);

            // instantiate a web framework to do the processing.  We're TDD'ing some new
            // code inside this.
            var webFramework = new WebFramework(context);

            // register a path to handle this pattern.  Wishful-thinking for the win.
            webFramework.registerPartialPath(GET, ".well-known/acme-challenge", request -> {
                String path = request.startLine().getPathDetails().isolatedPath();
                return Response.htmlOk("value was " + path);
            });

            // our code should properly find a handler for this endpoint
            var endpoint = webFramework.findEndpointForThisStartline(startLine);

            // now we create a whole request to stuff into this handler. We only
            // care about the startline.
            Request request = new Request(
                    new Headers(List.of(), context),
                    startLine,
                    Body.EMPTY(context),
                    ""
            );

            // when we run that handler against the request, it works.
            Response response = endpoint.apply(request);
            assertEquals(StringUtils.byteArrayToString(response.body()), "value was .well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX");
        }

        /*
        Playing a bit with some of keep-alive behavior
         */
        logger.test("If a client makes a HTTP/1.0 request, we'll check to see if it's keep-alive"); {

            final Function<StartLine, Function<Request, Response>> testHandler = (sl -> r -> {
               return Response.htmlOk("looking good!");
            });

            WebFramework wf = new WebFramework(context, default_zdt);
            try (Server primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
                try (var client = webEngine.startClient(primaryServer)) {
                    InputStream is = client.getInputStream();
                    Headers headers = Headers.make(context, inputStreamUtils);

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.0");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: keep-alive");
                    client.sendHttpLine("");

                    StatusLine statusLine1 = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine1.status(), _200_OK);
                    Headers headers1 = headers.extractHeaderInformation(is);
                    assertTrue(headers1.valueByKey("keep-alive").contains("timeout=3"));
                    new BodyProcessor(context).extractData(is, headers1);

                    // send another GET request, but closing it
                    client.sendHttpLine("GET /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: close");
                    client.sendHttpLine("");

                    StatusLine statusLine2 = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine2.status(), _200_OK);
                    Headers headers2 = headers.extractHeaderInformation(is);
                    assertTrue(headers2.valueByKey("keep-alive") == null);
                }
            }

            try (Server primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
                try (var client = webEngine.startClient(primaryServer)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.0");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: keep-alive");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine.status(), _200_OK);
                    Headers headers = Headers.make(context, inputStreamUtils);
                    Headers headers1 = headers.extractHeaderInformation(is);
                    assertTrue(headers1.valueByKey("keep-alive").contains("timeout=3"));
                }
            }
        }

        /*
        Noticed a failure in this code, adjusting to be more robust
         */
        logger.test("making isThereABody more robust"); {
            var webFramework = new WebFramework(context, default_zdt);

            // content-type: foo is illegitimate, but it will cause the system to look closer
            var headers1 = new Headers(List.of("content-type: foo"), context);
            assertFalse(webFramework.isThereIsABody(headers1));

            // transfer-encoding: foo is illegitimate, it will look for chunked but won't find it.
            var headers2 = new Headers(List.of("content-type: foo", "transfer-encoding: foo"), context);
            assertFalse(webFramework.isThereIsABody(headers2));

            // transfer-encoding: chunked is acceptable, it will return true here
            var headers3 = new Headers(List.of("content-type: foo", "transfer-encoding: chunked"), context);
            assertTrue(webFramework.isThereIsABody(headers3));
        }

        /*
        Since I recently saw a failure on the server due to missing testing of some logic
        branches, trying to get into a pattern of testing branches more robustly.
         */
        logger.test("making findHandlerByPartialMatch more robust"); {
            var webFramework = new WebFramework(context, default_zdt);
            Function<Request, Response> helloHandler = request -> Response.htmlOk("hello");

            // a situation where nothing was registered to the list of partial paths
            var startLine1 = new StartLine(GET, new StartLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
            assertTrue(webFramework.findHandlerByPartialMatch(startLine1) == null);

            // a complete match - not merely a partial match.  mypath == mypath
            webFramework.registerPartialPath(GET, "mypath", helloHandler);
            assertEquals(webFramework.findHandlerByPartialMatch(startLine1), helloHandler);

            // doesn't match
            var startLine2 = new StartLine(GET, new StartLine.PathDetails("mypa_DOES_NOT_MATCH", "", Map.of()), ONE_DOT_ONE, "", context);
            assertTrue(webFramework.findHandlerByPartialMatch(startLine2) == null);

            // doesn't match because different verb
            var startLine3 = new StartLine(POST, new StartLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
            assertTrue(webFramework.findHandlerByPartialMatch(startLine3) == null);

            // a poor choice of partial matcher - registering a path that will match too much
            webFramework.registerPartialPath(GET, "m", helloHandler);
            assertEquals(webFramework.findHandlerByPartialMatch(startLine1), helloHandler);

        }

        logger.test("Make the queryString method more robust"); {
            // if pathDetails is null, we'll get an empty hashmap
            var startLine1 = new StartLine(GET, null, ONE_DOT_ONE, "", context);
            assertEquals(startLine1.queryString(), new HashMap<>());

            // if the querystring in pathdetails is null, we'll get an empty hashmap
            var startLine2 = new StartLine(GET, new StartLine.PathDetails("mypath", "", null), ONE_DOT_ONE, "", context);
            assertEquals(startLine2.queryString(), new HashMap<>());

            // if the querystring in pathdetails is empty, we'll get an empty hashmap
            var startLine3 = new StartLine(GET, new StartLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
            assertEquals(startLine3.queryString(), new HashMap<>());
        }

        /**
         * The hash function we generated ought to get some use.
         * We'll pop StartLine in a hashmap to test it.
         */
        logger.test("Make sure we can enter startlines as keys in a hash map"); {
            var startLines = Map.of(
                    new StartLine(GET, new StartLine.PathDetails("foo", "", Map.of()), ONE_DOT_ONE, "", context),   "foo",
                    new StartLine(GET, new StartLine.PathDetails("bar", "", Map.of()), ONE_DOT_ONE, "", context),   "bar",
                    new StartLine(GET, new StartLine.PathDetails("baz", "", Map.of()), ONE_DOT_ONE, "", context),   "baz"
            );
            assertEquals(startLines.get(new StartLine(GET, new StartLine.PathDetails("bar", "", Map.of()), ONE_DOT_ONE, "", context)), "bar");
        }

        logger.test("Make the extractMapFromQueryString method more robust"); {
            // test when there's more query string key-value pairs than allowed by MAX_QUERY_STRING_KEYS_COUNT
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < context.getConstants().MAX_QUERY_STRING_KEYS_COUNT + 2; i++) {
                    sb.append(String.format("foo%d=bar%d&", i, i));
            }
            StartLine startLine = StartLine.EMPTY(context);
            assertThrows(ForbiddenUseException.class, () -> startLine.extractMapFromQueryString(sb.toString()));

            // if there is no equals sign in the query string
            var result = startLine.extractMapFromQueryString("foo");
            assertEquals(result, Map.of());
        }

        /*
         *  When we run the redirection handler, it will redirect all traffic
         * on the socket to the HTTPS endpoint.
         *
         *  Sometimes a client will connect to TCP but then close their
         * connection, in which case when we readline it will return as null,
         * and we'll return early from the handler, returning nothing.
         */
        logger.test("Typical happy path - a user makes an HTTP request to the insecure endpoint"); {
            var webFramework = new WebFramework(context);
            var redirectHandler = webFramework.makeRedirectHandler();
            FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
            fakeSocketWrapper.bais = new ByteArrayInputStream("The startline\n".getBytes(StandardCharsets.UTF_8));
            redirectHandler.accept(fakeSocketWrapper);
            String result = fakeSocketWrapper.baos.toString();
            assertTrue(result.contains("303 SEE OTHER"), "result was: " + result);
        }

        /*
         * Sometimes a client will connect to TCP but then close their
         * connection, in which case when we readline it will return as null,
         * and we'll return early from the handler, returning nothing.
         */
        logger.test("If the redirect handler receives no start line, return nothing"); {
            var webFramework = new WebFramework(context);
            var redirectHandler = webFramework.makeRedirectHandler();
            FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
            redirectHandler.accept(fakeSocketWrapper);
            assertEquals(fakeSocketWrapper.baos.toString(), "");
        }


    }

    private static byte[] makeTestMultiPartData() {
        try {
        /*
        Per the specs for multipart, the boundary is preceded by
        two dashes.
         */
            final var baos = new ByteArrayOutputStream();
            baos.write(
                """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                I am a value that is text\r
                --i_am_a_boundary\r
                Content-Type: application/octet-stream\r
                Content-Disposition: form-data; name="image_uploads"; filename="photo_preview.jpg"\r
                \r
                """.getBytes(StandardCharsets.UTF_8));
            baos.write(new byte[]{1, 2, 3});
            baos.write(
                """
                --i_am_a_boundary--
                """.getBytes(StandardCharsets.UTF_8));

            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String readBody(InputStream is, int length) throws IOException {
        return StringUtils.byteArrayToString(inputStreamUtils.read(length, is));
    }

}
