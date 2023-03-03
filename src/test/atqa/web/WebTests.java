package atqa.web;

import atqa.logging.TestLogger;
import atqa.utils.InvariantException;
import atqa.utils.StringUtils;
import atqa.utils.ThrowingConsumer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;

import static atqa.framework.TestFramework.*;
import static atqa.web.StartLine.startLineRegex;
import static atqa.web.StatusLine.StatusCode._200_OK;
import static atqa.web.StatusLine.StatusCode._404_NOT_FOUND;

public class WebTests {
    private final TestLogger logger;
    static final ZonedDateTime default_zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));

    public WebTests(TestLogger logger) {

        this.logger = logger;
    }

    /*$      /$$           /$$               /$$                           /$$
    | $$  /$ | $$          | $$              | $$                          | $$
    | $$ /$$$| $$  /$$$$$$ | $$$$$$$        /$$$$$$    /$$$$$$   /$$$$$$$ /$$$$$$   /$$$$$$$
    | $$/$$ $$ $$ /$$__  $$| $$__  $$      |_  $$_/   /$$__  $$ /$$_____/|_  $$_/  /$$_____/
    | $$$$_  $$$$| $$$$$$$$| $$  \ $$        | $$    | $$$$$$$$|  $$$$$$   | $$   |  $$$$$$
    | $$$/ \  $$$| $$_____/| $$  | $$        | $$ /$$| $$_____/ \____  $$  | $$ /$$\____  $$
    | $$/   \  $$|  $$$$$$$| $$$$$$$/        |  $$$$/|  $$$$$$$ /$$$$$$$/  |  $$$$//$$$$$$$/
    |__/     \__/ \_______/|_______/          \___/   \_______/|_______/    \___/ |______*/
    public void tests(ExecutorService es) throws IOException {

        WebEngine webEngine = new WebEngine(logger);

        logger.test("client / server");{
            try (Server primaryServer = webEngine.startServer(es)) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {
                    try (SocketWrapper server = primaryServer.getServer(client)) {

                        client.send("hello foo!\n");
                        String result = server.readLine();
                        assertEquals("hello foo!", result);
                    }
                }
                primaryServer.stop();
            }
        }

        logger.test("client / server with more conversation");{
            String msg1 = "hello foo!";
            String msg2 = "and how are you?";
            String msg3 = "oh, fine";

            try (Server primaryServer = webEngine.startServer(es)) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {
                    try (SocketWrapper server = primaryServer.getServer(client)) {

                        // client sends, server receives
                        client.sendHttpLine(msg1);
                        assertEquals(msg1, server.readLine());

                        // server sends, client receives
                        server.sendHttpLine(msg2);
                        assertEquals(msg2, client.readLine());

                        // client sends, server receives
                        client.sendHttpLine(msg3);
                        assertEquals(msg3, server.readLine());
                    }
                }
                primaryServer.stop();
            }
        }

        logger.test("What happens if we throw an exception in a thread");{
            es.submit(() -> {
                try {
                    throw new RuntimeException("No worries folks, just testing the exception handling, from test \"What happens if we throw an exception in a thread\"");
                } catch (Exception ex) {
                    logger.logDebug(() -> ex.getMessage());
                }
            });
        }

        logger.test("like we're a atqa.web server");{
            try (Server primaryServer = webEngine.startServer(es)) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {
                    try (SocketWrapper server = primaryServer.getServer(client)) {
                        // send a GET request
                        client.sendHttpLine("GET /index.html HTTP/1.1");
                        client.sendHttpLine("cookie: abc=123");
                        client.sendHttpLine("");
                        server.sendHttpLine("HTTP/1.1 200 OK");
                    }
                }
                primaryServer.stop();
            }
        }

    /*
      If we provide some code to handle things on the server
      side when it accepts a connection, then it will more
      truly act like the atqa.web server we want it to be.
     */
        logger.test("starting server with a handler");{
      /*
        Simplistic proof-of-concept of the atqa.primary server
        handler.  The socket has been created and as new
        clients call it, this method handles each request.

        There's nothing to prevent us using this as the entire
        basis of a atqa.web atqa.framework.
       */
            ThrowingConsumer<SocketWrapper, IOException> handler = (sw) -> logger.logDebug(sw::readLine);

            try (Server primaryServer = webEngine.startServer(es, handler)) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {
                    // send a GET request
                    client.sendHttpLine("GET /index.html HTTP/1.1");

                    primaryServer.stop();
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
                int bValue = Integer.parseInt(r.startLine().pathDetails().queryString().get("b"));
                int sum = aValue + bValue;
                String sumString = String.valueOf(sum);
                return new Response(_200_OK, List.of("Content-Type: text/html; charset=UTF-8"), sumString);
            }
        }

        logger.test("starting server with a handler part 2");{
            WebFramework wf = new WebFramework(es, logger, default_zdt);
            wf.registerPath(StartLine.Verb.GET, "add_two_numbers", Summation::addTwoNumbers);
            try (Server primaryServer = webEngine.startServer(es, wf.makeHandler())) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {
                    // send a GET request
                    client.sendHttpLine("GET /add_two_numbers?a=42&b=44 HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(client.readLine());

                    assertEquals(statusLine.rawValue(), "HTTP/1.1 200 OK");

                    Headers hi = Headers.extractHeaderInformation(client);

                    List<String> expectedResponseHeaders = Arrays.asList(
                            "Server: atqa",
                            "Date: Tue, 4 Jan 2022 09:25:00 GMT",
                            "Content-Type: text/html; charset=UTF-8",
                            "Content-Length: 2"
                    );

                    assertEqualsDisregardOrder(hi.headerStrings(), expectedResponseHeaders);

                    String body = readBody(client, hi.contentLength());

                    assertEquals(body, "86");

                    primaryServer.stop();
                }
            }
        }

        // Just a simple test while controlling the fake socket wrapper
        logger.test("TDD of a handler");{
            FakeSocketWrapper sw = new FakeSocketWrapper();
            AtomicReference<String> result = new AtomicReference<>();
            sw.sendHttpLineAction = s -> result.set(s);

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
            StartLine sl = StartLine.extractStartLine("POST /something HTTP/1.0");
            assertEquals(sl.verb(), StartLine.Verb.POST);
        }

        logger.test("alernate case - empty path");{
            StartLine sl = StartLine.extractStartLine("GET / HTTP/1.1");
            assertEquals(sl.verb(), StartLine.Verb.GET);
            assertEquals(sl.pathDetails().isolatedPath(), "");
        }

        logger.test("negative cases for extractStartLine");{
            // missing verb
            final var ex1 = assertThrows(InvariantException.class, "/something HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("/something HTTP/1.1"));
            assertEquals(ex1.getMessage(), "/something HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

            // missing path
            final var ex2 = assertThrows(InvariantException.class, "GET HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET HTTP/1.1"));
            assertEquals(ex2.getMessage(), "GET HTTP/1.1 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

            // missing HTTP version
            final var ex3 = assertThrows(InvariantException.class, "GET /something must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET /something"));
            assertEquals(ex3.getMessage(), "GET /something must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

            // invalid HTTP version
            final var ex4 = assertThrows(InvariantException.class, "GET /something HTTP/1.2 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET /something HTTP/1.2"));
            assertEquals(ex4.getMessage(), "GET /something HTTP/1.2 must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");

            // invalid HTTP version syntax
            final var ex5 = assertThrows(InvariantException.class, "GET /something HTTP/ must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$", () -> StartLine.extractStartLine("GET /something HTTP/"));
            assertEquals(ex5.getMessage(), "GET /something HTTP/ must match the startLinePattern: ^(GET|POST) /(.*) HTTP/(1.1|1.0)$");
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
            final var result = WebFramework.parseUrlEncodedForm("value_a=123&value_b=456");
            assertEquals(expected, result);
        }

        logger.test("parseUrlEncodedForm edge cases"); {
            // blank key
            final var ex2 = assertThrows(InvariantException.class, () -> WebFramework.parseUrlEncodedForm("=123"));
            assertEquals(ex2.getMessage(), "The key must not be blank");

            // duplicate keys
            final var ex3 = assertThrows(InvariantException.class, () -> WebFramework.parseUrlEncodedForm("a=123&a=123"));
            assertEquals(ex3.getMessage(), "a was duplicated in the post body - had values of 123 and 123");

            // empty value
            final var result = WebFramework.parseUrlEncodedForm("mykey=");
            assertEquals(result, Map.of("mykey", ""));
        }

        logger.test("when we post data to an endpoint, it can extract the data"); {
            WebFramework wf = new WebFramework(es, logger, default_zdt);
            wf.registerPath(StartLine.Verb.POST, "some_post_endpoint", request -> new Response(_200_OK, List.of("Content-Type: text/html; charset=UTF-8"),  request.bodyMap().get("value_a").toString()));
            try (Server primaryServer = webEngine.startServer(es, wf.makeHandler())) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {

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
                    final var statusLine = StatusLine.extractStatusLine(client.readLine());
                    Headers hi = Headers.extractHeaderInformation(client);
                    String body = readBody(client, hi.contentLength());

                    assertEquals(body, "123");

                    primaryServer.stop();
                }
            }
        }

        logger.test("when the requested endpoint does not exist, we get a 404 response"); {
            WebFramework wf = new WebFramework(es, logger, default_zdt);
            try (Server primaryServer = webEngine.startServer(es, wf.makeHandler())) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(client.readLine());
                    assertEquals(statusLine.rawValue(), "HTTP/1.1 404 NOT FOUND");

                    primaryServer.stop();
                }
            }
        }

        /*
         * The StaticFilesCache is memory storage for all the files we
         * are sending to clients that don't (typically) change during
         * the run of the server.  Things like style pages, banner images,
         * non-dynamic scripts.
         *
         * This test presumes that there are static files in the
         * main/static directory.
         */
        logger.test("StaticFilesCache should load static files into a map"); {
            final var sfc = new StaticFilesCache(logger).loadStaticFiles();
            assertTrue(sfc.getSize() > 0);
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
            final var headersIndicatingMultipart = List.of(
                    "Content-Type: multipart/form-data; boundary=i_am_a_boundary");

            /*
            Per the specs for multipart, the boundary is preceded by
            two dashes.
             */
            final var first = """
                    --i_am_a_boundary
                    Content-Type: text/plain
                    Content-Disposition: form-data; name="text1"
                    
                    I am a value that is text
                    --i_am_a_boundary
                    Content-Type: application/octet-stream
                    Content-Disposition: form-data; name="binary"
                    
                    """.getBytes(StandardCharsets.UTF_8);
            final var second = new byte[]{1,2,3};
            final var third = """
                    --i_am_a_boundary
                    """.getBytes(StandardCharsets.UTF_8);

            // combine the multipart data into one array
            byte[] multiPartData = Arrays.copyOf(first, first.length + second.length + third.length);
            System.arraycopy(second, 0, multiPartData, first.length, second.length);
            System.arraycopy(third, 0, multiPartData, first.length + second.length, third.length);

            // This is the core of the test - here's where we'll process receiving a multipart data

            final Function<StartLine, Function<Request, Response>> testHandler = (sl -> r -> {
                if (r.bodyMap().get("text1").equals("I am a value that is text") &&
                    r.bodyMap().get("binary").equals(new byte[]{1,2,3})) {
                    return new Response(
                            _200_OK,
                            List.of("Content-Type: text/html; charset=UTF-8"),
                            "<p>r was </p>");
                } else {
                  return new Response(_404_NOT_FOUND);
                }
            });

            WebFramework wf = new WebFramework(es, logger, default_zdt);
            try (Server primaryServer = webEngine.startServer(es, wf.makeHandler(testHandler))) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {

                    // send a GET request
                    client.sendHttpLine("POST /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Content-Type: multipart/form-data; boundary=i_am_a_boundary");
                    client.sendHttpLine("Content-length: " + multiPartData.length);
                    client.sendHttpLine("");
                    client.send(multiPartData);
                    client.closeWriting();

                    StatusLine statusLine = StatusLine.extractStatusLine(client.readLine());
                    assertEquals(statusLine.status(), _200_OK);

                    primaryServer.stop();
                }
            }
        }

        /*
        If a client is POSTing data to our server, there are two allowed ways of doing it
        in HTTP/1.1.  One is to include the content-length, and the other is to do
        transfer-encoding: chunked.  This test is to drive implementation of chunk encoding.
        see https://www.rfc-editor.org/rfc/rfc7230#section-3.3.3
         */
        logger.test("We should be able to handle transfer-encoding: chunked"); {
              /*
            Per the specs for multipart, the boundary is preceded by
            two dashes.
             */
            final var first = """
                    --i_am_a_boundary
                    Content-Type: text/plain
                    Content-Disposition: form-data; name="text1"
                    
                    I am a value that is text
                    --i_am_a_boundary
                    Content-Type: application/octet-stream
                    Content-Disposition: form-data; name="binary"
                    
                    """.getBytes(StandardCharsets.UTF_8);
            final var second = new byte[]{1,2,3};
            final var third = """
                    --i_am_a_boundary
                    """.getBytes(StandardCharsets.UTF_8);

            // combine the multipart data into one array
            byte[] multiPartData = Arrays.copyOf(first, first.length + second.length + third.length);
            System.arraycopy(second, 0, multiPartData, first.length, second.length);
            System.arraycopy(third, 0, multiPartData, first.length + second.length, third.length);

            final Function<StartLine, Function<Request, Response>> testHandler = (sl -> r -> {
                if (r.bodyMap().get("text1").equals("I am a value that is text") &&
                        r.bodyMap().get("binary").equals(new byte[]{1,2,3})) {
                    return new Response(
                            _200_OK,
                            List.of("Content-Type: text/html; charset=UTF-8"),
                            "<p>r was </p>");
                } else {
                    return new Response(_404_NOT_FOUND);
                }
            });

            WebFramework wf = new WebFramework(es, logger, default_zdt);
            try (Server primaryServer = webEngine.startServer(es, wf.makeHandler(testHandler))) {
                try (SocketWrapper client = webEngine.startClient(primaryServer)) {

                    // send a GET request
                    client.sendHttpLine("POST /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Content-Type: multipart/form-data; boundary=i_am_a_boundary");
                    client.sendHttpLine("Transfer-encoding: chunked");
                    client.sendHttpLine("");
                    client.send(multiPartData);
                    client.closeWriting();

                    StatusLine statusLine = StatusLine.extractStatusLine(client.readLine());
                    assertEquals(statusLine.status(), _200_OK);

                    primaryServer.stop();
                }
            }
        }

    }

    private static String readBody(SocketWrapper sw, int length) throws IOException {
        return StringUtils.bytesToString(sw.read(length));
    }

}
