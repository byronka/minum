package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.TheBrig;
import com.renomad.minum.testing.RegexUtils;
import com.renomad.minum.utils.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.RequestLine.Method.*;
import static com.renomad.minum.web.RequestLine.startLineRegex;
import static com.renomad.minum.web.HttpVersion.ONE_DOT_ONE;
import static com.renomad.minum.web.StatusLine.StatusCode.*;
import static com.renomad.minum.web.WebEngine.HTTP_CRLF;

/**
 <pre>
 /$      /$$           /$$               /$$                           /$$
| $$  /$ | $$          | $$              | $$                          | $$
| $$ /$$$| $$  /$$$$$$ | $$$$$$$        /$$$$$$    /$$$$$$   /$$$$$$$ /$$$$$$   /$$$$$$$
| $$/$$ $$ $$ /$$__  $$| $$__  $$      |_  $$_/   /$$__  $$ /$$_____/|_  $$_/  /$$_____/
| $$$$_  $$$$| $$$$$$$$| $$  \ $$        | $$    | $$$$$$$$|  $$$$$$   | $$   |  $$$$$$
| $$$/ \  $$$| $$_____/| $$  | $$        | $$ /$$| $$_____/ \____  $$  | $$ /$$\____  $$
| $$/   \  $$|  $$$$$$$| $$$$$$$/        |  $$$$/|  $$$$$$$ /$$$$$$$/  |  $$$$//$$$$$$$/
|__/     \__/ \_______/|_______/          \___/   \_______/|_______/    \___/ |_______/
 </pre>
In these tests, we have to include a sleep after each test that starts a server, so
that the next server won't conflict - it goes down to operating system code, and some
OS code has conflicts with servers restarting that fast. (Windows)
*/
public class WebTests {

    static final ZonedDateTime default_zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));
    static private ExecutorService es;
    static private IInputStreamUtils inputStreamUtils;
    static private Context context;
    static private WebEngine webEngine;
    static private TestLogger logger;
    private static String gettysburgAddress;
    /**
     * The length of time, in milliseconds, we will wait for the server to close
     * before letting computation continue.
     *
     * Otherwise, for some operating systems (Windows), there will be a conflict when the
     * next server bind occurs.
     */
    static int SERVER_CLOSE_WAIT_TIME = 30;

    @BeforeClass
    public static void setUpClass() throws IOException {
        var properties = new Properties();
        properties.setProperty("SERVER_PORT", "7777");
        properties.setProperty("SUSPICIOUS_PATHS", ".env");
        properties.setProperty("IS_THE_BRIG_ENABLED", "true");
        context = buildTestingContext("unit_tests",properties);
        webEngine = new WebEngine(context);
        es = context.getExecutorService();
        inputStreamUtils = context.getInputStreamUtils();
        logger = (TestLogger)context.getLogger();
        gettysburgAddress = Files.readString(Path.of("src/test/resources/gettysburg_address.txt"));
    }


    @AfterClass
    public static void tearDownClass() {
        context.getFileUtils().deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory), logger);
        shutdownTestingContext(context);
    }

    private static void accept(String x) throws IOException{
        throw new IOException("testing");
    }

    @Test
    public void test_basicClientServer() throws Exception {
        try (IServer primaryServer = webEngine.startServer(es)) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (ISocketWrapper client = webEngine.startClient(socket)) {
                    try (var server = primaryServer.getServer(client)) {
                        InputStream is = server.getInputStream();

                        client.send("hello foo!\n");
                        String result = inputStreamUtils.readLine(is);
                        assertEquals("hello foo!", result);
                    }
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    @Test
    public void test_basicClientServer_MoreConversation() throws Exception {
        String msg1 = "hello foo!";
        String msg2 = "and how are you?";
        String msg3 = "oh, fine";

        try (var primaryServer = webEngine.startServer(es)) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
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
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    /**
     * What happens if we throw an exception in a thread?
     */
    @Test
    public void test_ThrowingExceptionInThread() {
        es.submit(() -> {
            try {
                throw new RuntimeException("No worries folks, just testing the exception handling, from test \"What happens if we throw an exception in a thread\"");
            } catch (Exception ex) {
                logger.logDebug(ex::getMessage);
            }
        });
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
        assertTrue(logger.doesMessageExist("No worries folks, just testing the exception handling", 8));
    }

    /**
     * What would it be like if we were a real web server?
     */
    @Test
    public void test_LikeARealWebServer() throws Exception {
        try (var primaryServer = webEngine.startServer(es)) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
                    try (var server = primaryServer.getServer(client)) {
                        // send a GET request
                        client.sendHttpLine("GET /index.html HTTP/1.1");
                        client.sendHttpLine("cookie: abc=123");
                        client.sendHttpLine("");
                        server.sendHttpLine("HTTP/1.1 200 OK");
                        assertTrue(logger.doesMessageExist("HTTP/1.1 200 OK"));
                    }
                }
            }
        }

        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    /**
     * If we provide some code to handle things on the server
     * side when it accepts a connection, then it will more
     * truly act like the minum.web server we want it to be.
     */
    @Test
    public void test_StartingServerWithHandler() throws Exception {
       /*
        Simplistic proof-of-concept of the minum.primary server
        handler.  The socket has been created and as new
        clients call it, this method handles each request.
       */
        ThrowingConsumer<ISocketWrapper> handler = (sw) -> {
            InputStream is = sw.getInputStream();
            logger.logDebug(() -> inputStreamUtils.readLine(is));
            assertTrue(logger.doesMessageExist("GET /index.html HTTP/1.1"));
        };

        try (IServer primaryServer = webEngine.startServer(es, handler)) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
                    // send a GET request
                    client.sendHttpLine("GET /index.html HTTP/1.0");
                    client.sendHttpLine("");
                    MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    /**
     * This class belongs to the test below, "starting server with a handler part 2"
     * This represents the shape an endpoint could take. It's given a request object,
     * we presume it has everything is needs to do its work (query strings on the path,
     * header values, body contents, all that stuff), and it replies with a response
     * object that gets ferried along to the client.
     */
    class Summation {
        static Response addTwoNumbers(Request r) {
            int aValue = Integer.parseInt(r.requestLine().queryString().get("a"));
            int bValue = Integer.parseInt(r.requestLine().getPathDetails().queryString().get("b"));
            int sum = aValue + bValue;
            String sumString = String.valueOf(sum);
            return Response.htmlOk(sumString);
        }
    }

    /**
     * A more realistic use case of the Minum server
     */
    @Test
    public void test_StartingWithHandler_Realistic() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        wf.registerPath(GET, "add_two_numbers", Summation::addTwoNumbers);
        try (IServer primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /add_two_numbers?a=42&b=44 HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

                    assertEquals(statusLine.rawValue(), "HTTP/1.1 200 OK");

                    Headers hi = Headers.make(context).extractHeaderInformation(is);

                    assertEquals(hi.valueByKey("server"), List.of("minum"));
                    assertTrue(hi.valueByKey("date") != null);
                    assertEquals(hi.valueByKey("content-type"), List.of("text/html; charset=UTF-8"));
                    assertEquals(hi.valueByKey("content-length"), List.of("2"));

                    String body = readBody(is, hi.contentLength());

                    assertEquals(body, "86");
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    @Test
    public void test_TDD_ofHandler() throws Exception {
        FakeSocketWrapper sw = new FakeSocketWrapper();
        AtomicReference<String> result = new AtomicReference<>();
        sw.sendHttpLineAction = result::set;

        // this is what we're really going to test
        ThrowingConsumer<ISocketWrapper> handler = (socketWrapper) -> socketWrapper.sendHttpLine("this is a test");
        handler.accept(sw);

        assertEquals("this is a test", result.get());
    }

    /**
     * See https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line
     * 1. the method (GET, POST, etc.)
     * 2. the request target
     * 3. the HTTP version (e.g. HTTP/1.1)
     */
    @Test
    public void test_StartLine_HappyPath() {
        Matcher m = startLineRegex.matcher("GET /index.html HTTP/1.1");
        assertTrue(m.matches());
    }

    @Test
    public void test_StartLine_Post() {
        RequestLine sl = RequestLine.empty(context).extractRequestLine("POST /something HTTP/1.0");
        assertEquals(sl.getMethod(), POST);
    }

    @Test
    public void test_StartLine_EmptyPath() {
        RequestLine sl = RequestLine.empty(context).extractRequestLine("GET / HTTP/1.1");
        assertEquals(sl.getMethod(), GET);
        assertEquals(sl.getPathDetails().isolatedPath(), "");
    }

    @Test
    public void test_StartLine_MissingMethod() {
            // missing method
            List<String> badStartLines = List.of(
                    "/something HTTP/1.1",
                    "GET HTTP/1.1",
                    "GET /something",
                    "GET /something HTTP/1.2",
                    "GET /something HTTP/",
                    ""
            );
            for (String s : badStartLines) {
                assertEquals(RequestLine.empty(context).extractRequestLine(s), RequestLine.empty(context));
            }
            assertThrows(InvariantException.class, () -> RequestLine.empty(context).extractRequestLine(null));
    }

    @Test
    public void test_StatusLine_HappyPath() {
        StatusLine sl = StatusLine.extractStatusLine("HTTP/1.1 200 OK");
        assertEquals(sl.status(), CODE_200_OK);
    }

    @Test
    public void test_StatusLine_HappyPath_1_0() {
        StatusLine sl = StatusLine.extractStatusLine("HTTP/1.0 200 OK");
        assertEquals(sl.status(), CODE_200_OK);
    }

    @Test
    public void test_StatusLine_MissingStatusDescription() {
        assertThrows(InvariantException.class,
                "HTTP/1.1 200 must match the statusLinePattern: ^HTTP/(...) (\\d{3}) (.*)$",
                () -> StatusLine.extractStatusLine("HTTP/1.1 200"));
    }

    @Test
    public void test_StatusLine_MissingStatusCode() {
        assertThrows(InvariantException.class,
                "HTTP/1.1  OK must match the statusLinePattern: ^HTTP/(...) (\\d{3}) (.*)$",
                () -> StatusLine.extractStatusLine("HTTP/1.1  OK"));
    }

    @Test
    public void test_StatusLine_MissingHttpVersion() {
        assertThrows(InvariantException.class,
                "HTTP 200 OK must match the statusLinePattern: ^HTTP/(...) (\\d{3}) (.*)$",
                () -> StatusLine.extractStatusLine("HTTP 200 OK"));
    }

    @Test
    public void test_StatusLine_InvalidHttpVersion() {
        assertThrows(RuntimeException.class,
                "HTTP version was not an acceptable value. Given: 1.3",
                () -> StatusLine.extractStatusLine("HTTP/1.3 200 OK"));
    }

    @Test
    public void test_StatusLine_InvalidStatusCode() {
        assertThrows(NoSuchElementException.class,
                "No value present",
                () -> StatusLine.extractStatusLine("HTTP/1.1 199 OK"));
    }

    @Test
    public void test_StatusLine_nullStatusLine() {
        assertEquals(StatusLine.extractStatusLine(null), StatusLine.EMPTY);
    }

    /*
    as part of sending data to the server, we'll encode data like the following.  if we
    set value_a to 123 and value_b to 456, it looks like: value_a=123&value_b=456

    we want to convert that string to a map, like this: value_a -> 123, value_b -> 456
     */

    @Test
    public void test_ParseForm_UrlEncoded() {
        final var expected = Map.of("value_a", "123", "value_b", "456");
        final var result = new BodyProcessor(context).parseUrlEncodedForm("value_a=123&value_b=456");
        assertEquals(expected.get("value_a"), result.asString("value_a"));
        assertEquals(expected.get("value_b"), result.asString("value_b"));
    }

    @Test
    public void test_ParseForm_EdgeCase_BlankKey() {
        new BodyProcessor(context).parseUrlEncodedForm("=123");
        var logs = logger.findFirstMessageThatContains("The key must not be blank");
        assertTrue(!logs.isBlank());
    }

    @Test
    public void test_ParseForm_EdgeCase_DuplicateKey() {
        new BodyProcessor(context).parseUrlEncodedForm("a=123&a=123");
        var logs = logger.findFirstMessageThatContains("a was duplicated in the post body - had values of 123 and 123");
        assertTrue(!logs.isBlank());
    }

    @Test
    public void test_ParseForm_EdgeCase_EmptyValue() {
        final var result = new BodyProcessor(context).parseUrlEncodedForm("mykey=");
        assertEquals(result.asString("mykey"), "");
    }

    @Test
    public void test_ParseForm_EdgeCase_NullValue() {
        final var result2 = new BodyProcessor(context).parseUrlEncodedForm("mykey=%NULL%");
        assertEquals(result2.asString("mykey"), "");
    }

    /**
     * What happens if we are dealing with a form having many values?
     * By the way, the maximum allowed is set by {@link com.renomad.minum.Constants#maxTokenizerPartitions}
     */
    @Test
    public void test_ParseForm_EdgeCase_ManyFormValues() {
        final var result2 = new BodyProcessor(context)
                .parseUrlEncodedForm("foo0=&" +
                        "foo1=&" +
                        "foo2=bar&" +
                        "foo3=&" +
                        "foo4=&" +
                        "foo5=&" +
                        "foo6=&" +
                        "foo7=&" +
                        "foo8=&" +
                        "foo9=&" +
                        "foo10=&" +
                        "foo11=3&" +
                        "foo12=wedding+date&" +
                        "foo13=date&" +
                        "foo14=2006-9-18&" +
                        "foo15=graduation+date&" +
                        "foo16=date&" +
                        "foo17=1998-04-04&" +
                        "foo18=birthplace+-+city&" +
                        "foo19=string&" +
                        "foo20=Encino");
        assertEquals(result2.asString("foo18"), "birthplace - city");
    }

    @Test
    public void test_ParseForm_Empty() {
        final var result = new BodyProcessor(context).parseUrlEncodedForm("");
        assertEquals(result, Body.EMPTY);
    }

    @Test
    public void test_ParseForm_MoreRealisticCase() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        try (var primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
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
                    Headers hi = Headers.make(context).extractHeaderInformation(client.getInputStream());
                    String body = readBody(is, hi.contentLength());

                    assertEquals(body, "123");
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    @Test
    public void test_NotFoundPath() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        try (var primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
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
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    /**
     * If a client is POSTing data to our server, there are two allowed ways of doing it
     * in HTTP/1.1.  One is to include the content-length, and the other is to do
     * transfer-encoding: chunked.  This test is to drive implementation of chunk encoding.
     * see https://www.rfc-editor.org/rfc/rfc7230#section-3.3.3
     */
    @Test
    public void test_TransferEncodingChunked() throws Exception {
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

    /**
     * Looking into how to split a byte array using a string delimiter
     */
    @Test
    public void test_MutiPartForm_Splitting() {
        byte[] multiPartData = makeTestMultiPartData();
        var bp = new BodyProcessor(context);

        List<byte[]> result = bp.split(multiPartData, "\r\n--i_am_a_boundary");

        assertEquals(result.size(), 2);
        assertEquals(new String(result.get(0)), "Content-Type: text/plain\r\n" +
                "Content-Disposition: form-data; name=\"text1\"\r\n" +
                "\r\n" +
                "I am a value that is text");
        assertEquals(new String(result.get(1)), "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: form-data; name=\"image_uploads\"; filename=\"photo_preview.jpg\"\r\n" +
                "\r\n" +
                "\u0001\u0002\u0003");
    }

    /**
     * Examining the algorithm for parsing multipart data
     */
    @Test
    public void test_MultiPartForm_Algorithm() {
        byte[] multiPartData = makeTestMultiPartData();
        var bp = new BodyProcessor(context);

        Body result = bp.parseMultiform(multiPartData, "i_am_a_boundary");

        assertEquals(result.asString("text1"), "I am a value that is text");
        assertEqualByteArray(result.asBytes("image_uploads"), new byte[]{1, 2, 3});
    }

    @Test
    public void test_MultiPartForm_NoContentDisposition() {
        byte[] multiPartData = makeTestMultiPartDataNoContentDisposition();
        var bp = new BodyProcessor(context);

        Body result = bp.parseMultiform(multiPartData, "i_am_a_boundary");
        assertTrue(result.asString().contains("I am a value that is text"));
    }

    /**
     * Each part of the multipart data has its own headers. Let's
     * make sure to convey this information to users.
     * <p>
     *     For example, it might include the mime type for the data.
     * </p>
     */
    @Test
    public void test_MultiPartForm_GetHeadersPerPartition() {
        byte[] multiPartData = makeTestMultiPartData();
        var bp = new BodyProcessor(context);

        Body result = bp.parseMultiform(multiPartData, "i_am_a_boundary");

        assertEquals(result.asString("text1"), "I am a value that is text");
        assertEquals(result.partitionHeaders("text1").valueByKey("content-type"), List.of("text/plain"));
        assertEquals(result.partitionHeaders("text1").valueByKey("content-disposition"), List.of("form-data; name=\"text1\""));
        assertEqualByteArray(result.asBytes("image_uploads"), new byte[]{1, 2, 3});
    }


    /**
     * There are two primary ways to send data in requests and responses.
     * <ol>
     * <li>
     * Url encoding - this is what we have been doing so far, converting
     *    some values to percent-encoded.
     * </li>
     * <li>
     * Multipart - providing a boundary token of our choice, along with
     *    a couple headers to indicate critical information, then a blank line,
     *    then the raw content.
     * </li>
     *</ol>
     * <p>
     * This test will cause us to create a program that is able to read each
     * section of the multipart format.  The interesting/difficult problem
     * I'm anticipating is that we need to be able to read binary and ascii.
     *</p>
     * <p>
     * See, we will only read until we encounter the boundary text, which might
     * be "abc123".  So if we read byte-by-byte, as soon as we hit a byte that
     * can be "a", we need to jump into the possibility that we may be looking
     * at the beginning of "abc123".
     * </p>
     */
    @Test
    public void test_MultiPartForm_HappyPath() throws Exception {
        byte[] multiPartData = makeTestMultiPartData();

        // This is the core of the test - here's where we'll process receiving a multipart data

        final ThrowingFunction<RequestLine, ThrowingFunction<Request, Response>> testHandler = (sl -> r -> {
            if (r.body().asString("text1").equals("I am a value that is text") &&
                    (r.body().asBytes("image_uploads")).length == 3 &&
                    (r.body().asBytes("image_uploads"))[0] == 1 &&
                    (r.body().asBytes("image_uploads"))[1] == 2 &&
                    (r.body().asBytes("image_uploads"))[2] == 3
            ) {
                return Response.htmlOk("<p>everything was ok</p>");
            } else {
              return new Response(CODE_404_NOT_FOUND);
            }
        });

        WebFramework wf = new WebFramework(context, default_zdt);
        try (IServer primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (ISocketWrapper client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("POST /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Content-Type: multipart/form-data; boundary=i_am_a_boundary");
                    client.sendHttpLine("Content-length: " + multiPartData.length);
                    client.sendHttpLine("");
                    client.send(multiPartData);

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine.status(), CODE_200_OK);
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    private boolean isEqualByteArray(byte[] left, byte[] right) {
        if (left == null || right == null) {
            logger.logDebug(() -> "at least one of the inputs was null: left: %s right: %s".formatted(Arrays.toString(left), Arrays.toString(right)));
            return false;
        }
        if (left.length != right.length) {
            logger.logDebug(() -> "Not equal! left length: %d right length: %d".formatted(left.length, right.length));
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                int finalI = i;
                int finalI1 = i;
                logger.logDebug(() -> "Not equal! at index %d left was: %d right was: %d".formatted(finalI, left[finalI1], right[finalI1]));
                return false;
            }
        }
        return true;
    }

    /**
     * Similar to {@link #test_MultiPartForm_HappyPath()} but uses a multipart-form
     * data that has a couple large binaries - jpeg's of a kitty
     */
    @Test
    public void test_MultiPartForm_HappyPath_MoreImages() throws Exception {
        byte[] multiPartData = makeTestMultiPartData_MultipleImages();

        // This is the core of the test - here's where we'll process receiving a multipart data

        final ThrowingFunction<RequestLine, ThrowingFunction<Request, Response>> testHandler = (sl -> r -> {
            byte[] kittyPic = Files.readAllBytes(Path.of("src/test/resources/kitty.jpg"));
            if (r.body().asString("text1").equals("I am a value that is text") &&
                    isEqualByteArray(r.body().asBytes("image_uploads"), new byte[]{1,2,3}) &&
                    isEqualByteArray(r.body().asBytes("kitty1"), kittyPic) &&
                    isEqualByteArray(r.body().asBytes("kitty2"), kittyPic)
            ) {
                return Response.htmlOk("<p>everything was ok</p>");
            } else {
              return new Response(CODE_404_NOT_FOUND);
            }
        });

        WebFramework wf = new WebFramework(context, default_zdt);
        try (IServer primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (ISocketWrapper client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("POST /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Content-Type: multipart/form-data; boundary=i_am_a_boundary");
                    client.sendHttpLine("Content-length: " + multiPartData.length);
                    client.sendHttpLine("");
                    client.send(multiPartData);

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine.status(), CODE_200_OK);
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    @Test
    public void test_Headers_Multiple() {
        Headers headers = new Headers(List.of("foo: a", "foo: b"), context);
        assertEqualsDisregardOrder(headers.valueByKey("foo"), List.of("a","b"));
    }

    /**
     * If we just stayed with path plus query string....  But no, it's boring, I guess, to
     * have consistency and safety, so people decided we should have paths
     * with a pattern like /my/path/{id}.  unnecessary.  stupid.  whatever,
     * it's the world I have to live in.  It's a bit complicated of a test, so I'll explain as I go.
     */
    @Test
    public void test_Path_InsaneWorld() throws Exception {
        // The startline causing us heartache
        String startLineString = "GET /.well-known/acme-challenge/foobar HTTP/1.1";

        // Convert it to a typed value
        var startLine = RequestLine.empty(context).extractRequestLine(startLineString);

        // instantiate a web framework to do the processing.  We're TDD'ing some new
        // code inside this.
        var webFramework = new WebFramework(context);

        // register a path to handle this pattern.  Wishful-thinking for the win.
        webFramework.registerPartialPath(GET, ".well-known/acme-challenge", request -> {
            String path = request.requestLine().getPathDetails().isolatedPath();
            return Response.htmlOk("value was " + path);
        });

        // our code should properly find a handler for this endpoint
        var endpoint = webFramework.findEndpointForThisStartline(startLine);

        // now we create a whole request to stuff into this handler. We only
        // care about the startline.
        Request request = new Request(
                new Headers(List.of(), context),
                startLine,
                Body.EMPTY,
                ""
        );

        // when we run that handler against the request, it works.
        Response response = endpoint.apply(request);
        assertEquals(StringUtils.byteArrayToString(response.body()), "value was .well-known/acme-challenge/foobar");
    }

    /**
     * Playing a bit with some of keep-alive behavior
     */
    @Test
    public void test_KeepAlive_Http_1_0() throws Exception {

        final ThrowingFunction<RequestLine, ThrowingFunction<Request, Response>> testHandler = (sl -> r -> Response.htmlOk("looking good!"));

        WebFramework wf = new WebFramework(context, default_zdt);
        try (IServer primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (ISocketWrapper client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();
                    Headers headers = Headers.make(context);

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.0");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: keep-alive");
                    client.sendHttpLine("");

                    StatusLine statusLine1 = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine1.status(), CODE_200_OK);
                    Headers headers1 = headers.extractHeaderInformation(is);
                    assertTrue(headers1.valueByKey("keep-alive").contains("timeout=3"));
                    new BodyProcessor(context).extractData(is, headers1);

                    // send another GET request, but closing it
                    client.sendHttpLine("GET /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: close");
                    client.sendHttpLine("");

                    StatusLine statusLine2 = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine2.status(), CODE_200_OK);
                    Headers headers2 = headers.extractHeaderInformation(is);
                    assertTrue(headers2.valueByKey("keep-alive") == null);
                }
            }
        }

        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);

        try (IServer primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler(testHandler))) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (ISocketWrapper client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.0");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: keep-alive");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine.status(), CODE_200_OK);
                    Headers headers = Headers.make(context);
                    Headers headers1 = headers.extractHeaderInformation(is);
                    assertTrue(headers1.valueByKey("keep-alive").contains("timeout=3"));
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);

    }

    @Test
    public void test_InvalidRequestLine() throws Exception {
        WebFramework wf = new WebFramework(context, default_zdt);
        try (IServer primaryServer = webEngine.startServer(es, wf.makePrimaryHttpHandler())) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
                    // send an invalid GET request
                    client.sendHttpLine("FOOP FOOP FOOP");
                    client.sendHttpLine("");
                    MyThread.sleep(10);
                    assertTrue(logger.doesMessageExist("RequestLine was unparseable.  Returning.", 20));
                }
            }
        }
        assertTrue(logger.doesMessageExist("close called on http server", 20));
        MyThread.sleep(10);

        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }


    /**
     * If there are errors when the server gets instantiated, it throws
     * an error
     */
    @Test
    public void test_InvalidPort() {
        var properties = new Properties();
        properties.setProperty("SERVER_PORT", "999999999");
        Context context = buildTestingContext("testing invalid port",properties);
        try {
            WebEngine webEngine = new WebEngine(context);
            assertThrows(WebServerException.class, () -> webEngine.startServer(es, null));
            MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
        } finally {
            shutdownTestingContext(context);
        }
    }

    @Test
    public void test_IsThereABody_ContentType() {
        // content-type: foo is illegitimate, but it will cause the system to look closer
        var headers1 = new Headers(List.of("content-type: foo"), context);
        assertFalse(WebFramework.isThereIsABody(headers1));
    }

    @Test
    public void test_IsThereABody_TransferEncodingFoo() {
        // transfer-encoding: foo is illegitimate, it will look for chunked but won't find it.
        var headers2 = new Headers(List.of("content-type: foo", "transfer-encoding: foo"), context);
        assertFalse(WebFramework.isThereIsABody(headers2));
    }

    @Test
    public void test_IsThereABody_TransferEncodingChunked() {
        // transfer-encoding: chunked is acceptable, it will return true here
        var headers3 = new Headers(List.of("content-type: foo", "transfer-encoding: chunked"), context);
        assertTrue(WebFramework.isThereIsABody(headers3));
    }

    /**
     * a situation where nothing was registered to the list of partial paths
     */
    @Test
    public void test_PartialMatch_NothingRegistered() {
        var webFramework = new WebFramework(context, default_zdt);

        var startLine = new RequestLine(GET, new RequestLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
        assertTrue(webFramework.findHandlerByPartialMatch(startLine) == null);
    }

    /**
     * a complete match - not merely a partial match.  mypath == mypath
     */
    @Test
    public void test_PartialMatch_PerfectMatch() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<Request, Response> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);

        var startLine = new RequestLine(GET, new RequestLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
        assertEquals(webFramework.findHandlerByPartialMatch(startLine), helloHandler);
    }

    @Test
    public void test_PartialMatch_DoesNotMatch() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<Request, Response> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);

        var startLine = new RequestLine(GET, new RequestLine.PathDetails("mypa_DOES_NOT_MATCH", "", Map.of()), ONE_DOT_ONE, "", context);
        assertTrue(webFramework.findHandlerByPartialMatch(startLine) == null);
    }

    @Test
    public void test_PartialMatch_DifferentMethod() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<Request, Response> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);

        var startLine = new RequestLine(POST, new RequestLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
        assertTrue(webFramework.findHandlerByPartialMatch(startLine) == null);
    }

    @Test
    public void test_PartialMatch_MatchTooMuch() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<Request, Response> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);
        webFramework.registerPartialPath(GET, "m", helloHandler);

        var startLine = new RequestLine(GET, new RequestLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
        assertEquals(webFramework.findHandlerByPartialMatch(startLine), helloHandler);
    }

    /**
     * if pathDetails is null, we'll get an empty hashmap
     */
    @Test
    public void test_QueryString_NullPathdetails() {
        var startLine = new RequestLine(GET, null, ONE_DOT_ONE, "", context);
        assertEquals(startLine.queryString(), new HashMap<>());
    }

    @Test
    public void test_QueryString_NullQueryString() {
        var startLine = new RequestLine(GET, new RequestLine.PathDetails("mypath", "", null), ONE_DOT_ONE, "", context);
        assertEquals(startLine.queryString(), new HashMap<>());
    }

    @Test
    public void test_QueryString_EmptyQueryString() {
        var startLIne = new RequestLine(GET, new RequestLine.PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", context);
        assertEquals(startLIne.queryString(), new HashMap<>());
    }

    /**
     * The hash function we generated ought to get some use.
     * We'll pop StartLine in a hashmap to test it.
     */
    @Test
    public void test_StartLine_Hashing() {
        var startLines = Map.of(
                new RequestLine(GET, new RequestLine.PathDetails("foo", "", Map.of()), ONE_DOT_ONE, "", context),   "foo",
                new RequestLine(GET, new RequestLine.PathDetails("bar", "", Map.of()), ONE_DOT_ONE, "", context),   "bar",
                new RequestLine(GET, new RequestLine.PathDetails("baz", "", Map.of()), ONE_DOT_ONE, "", context),   "baz"
        );
        assertEquals(startLines.get(new RequestLine(GET, new RequestLine.PathDetails("bar", "", Map.of()), ONE_DOT_ONE, "", context)), "bar");
    }

    /**
     * test when there's more query string key-value pairs than allowed by MAX_QUERY_STRING_KEYS_COUNT
     */
    @Test
    public void test_ExtractMapFromQueryString_TooManyPairs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < context.getConstants().maxQueryStringKeysCount + 2; i++) {
                sb.append(String.format("foo%d=bar%d&", i, i));
        }
        RequestLine requestLine = RequestLine.empty(context);
        assertThrows(ForbiddenUseException.class, () -> requestLine.extractMapFromQueryString(sb.toString()));
    }

    /**
     * if there is no equals sign in the query string
     */
    @Test
    public void test_ExtractMapFromQueryString_NoEqualsSign() {
        RequestLine requestLine = RequestLine.empty(context);
        var result = requestLine.extractMapFromQueryString("foo");
        assertEquals(result, Map.of());
    }

    /**
     *  When we run the redirection handler, it will redirect all traffic
     * on the socket to the HTTPS endpoint.
     *  Sometimes a client will connect to TCP but then close their
     * connection, in which case when we readline it will return as null,
     * and we'll return early from the handler, returning nothing.
     */
    @Test
    public void test_RedirectHandler_HappyPath() throws Exception {
        var webFramework = new WebFramework(context);
        var redirectHandler = webFramework.makeRedirectHandler();
        FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
        fakeSocketWrapper.bais = new ByteArrayInputStream("The startline\n".getBytes(StandardCharsets.UTF_8));
        redirectHandler.accept(fakeSocketWrapper);
        String result = fakeSocketWrapper.baos.toString();
        assertTrue(result.contains("303 SEE OTHER"), "result was: " + result);
    }

    /**
     * Sometimes a client will connect to TCP but then close their
     * connection, in which case when we read the line it will return as null,
     * and we'll return early from the handler, returning nothing.
     */
    @Test
    public void test_RedirectHandler_NoStartLine() throws Exception {
        var webFramework = new WebFramework(context);
        var redirectHandler = webFramework.makeRedirectHandler();
        var fakeSocketWrapper = new FakeSocketWrapper();
        redirectHandler.accept(fakeSocketWrapper);
        assertEquals(fakeSocketWrapper.baos.toString(), "");
    }

    @Test
    public void test_RedirectHandler_EmptyStartLine() throws Exception {
        var webFramework = new WebFramework(context);
        var redirectHandler = webFramework.makeRedirectHandler();
        var fakeSocketWrapper = new FakeSocketWrapper();
        fakeSocketWrapper.bais = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        redirectHandler.accept(fakeSocketWrapper);
        assertEquals(fakeSocketWrapper.baos.toString(), "");
    }

    /**
     * Test what happens when an error occurs during readLine
     */
    @Test
    public void test_RedirectHandler_ExceptionThrown_AtReadLine() {
        try (var fakeSocketWrapper = new FakeSocketWrapper()) {
            var throwingInputStreamUtils = new IInputStreamUtils() {

                @Override
                public byte[] readUntilEOF(InputStream inputStream) {
                    return new byte[0];
                }

                @Override
                public byte[] readChunkedEncoding(InputStream inputStream) {
                    return new byte[0];
                }

                @Override
                public String readLine(InputStream inputStream) throws IOException {
                    throw new IOException("This is a test");
                }

                @Override
                public byte[] read(int lengthToRead, InputStream inputStream) {
                    return new byte[0];
                }
            };
            assertThrows(WebServerException.class,
                    "java.io.IOException: This is a test",
                    () -> WebFramework.redirectHandlerCore(
                            fakeSocketWrapper,
                            throwingInputStreamUtils,
                            context,
                            "testing",
                            logger));
        }
    }

    /**
     * Test what happens when an error occurs during sending
     */
    @Test
    public void test_RedirectHandler_ExceptionThrown_AtSending() {
        try (var fakeSocketWrapper = new ThrowingFakeSocketWrapper()) {
            var throwingInputStreamUtils = new IInputStreamUtils() {

                @Override
                public byte[] readUntilEOF(InputStream inputStream) {
                    return new byte[0];
                }

                @Override
                public byte[] readChunkedEncoding(InputStream inputStream) {
                    return new byte[0];
                }

                @Override
                public String readLine(InputStream inputStream) {
                    return "testing";
                }

                @Override
                public byte[] read(int lengthToRead, InputStream inputStream) {
                    return new byte[0];
                }
            };

            assertThrows(WebServerException.class,
                    "java.io.IOException: testing send(String)",
                    () -> WebFramework.redirectHandlerCore(
                            fakeSocketWrapper,
                            throwingInputStreamUtils,
                            context,
                            "testing",
                            logger));
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
                \r
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
                \r
                --i_am_a_boundary--\r
                
                """.getBytes(StandardCharsets.UTF_8));

            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This creates a multipart form data containing multiple
     * JPEG files
     */
    private static byte[] makeTestMultiPartData_MultipleImages() {
        try {
        /*
        Per the specs for multipart, the boundary is preceded by
        two dashes.
         */
            Path kittyPath = Path.of("src/test/resources/kitty.jpg");
            byte[] kittyBytes = Files.readAllBytes(kittyPath);
            final var baos = new ByteArrayOutputStream();
            baos.write(
                """
                \r
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
            baos.write("""
                        \r
                        --i_am_a_boundary\r
                        Content-Type: application/octet-stream\r
                        Content-Disposition: form-data; name="kitty1"; filename="kitty1.jpg"\r
                        \r
                        """.getBytes(StandardCharsets.UTF_8));
            baos.write(kittyBytes);
            baos.write("""
                        \r
                        --i_am_a_boundary\r
                        Content-Type: application/octet-stream\r
                        Content-Disposition: form-data; name="kitty2"; filename="kitty2.jpg"\r
                        \r
                        """.getBytes(StandardCharsets.UTF_8));
            baos.write(kittyBytes);
            baos.write(
                    """
                    \r
                    --i_am_a_boundary--\r
                    
                    """.getBytes(StandardCharsets.UTF_8));


            return baos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    private static byte[] makeTestMultiPartDataNoContentDisposition() {
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
                \r
                I am a value that is text\r
                --i_am_a_boundary\r
                Content-Type: application/octet-stream\r
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

    private String readBody(InputStream is, int length) {
        return StringUtils.byteArrayToString(inputStreamUtils.read(length, is));
    }

    /**
     * A client may request for data to be compressed, using the Accept-Encoding
     * header.  For example, "Accept-Encoding: gzip" will tell us that the client
     * will be able to decompress gzip.
     */
    @Test
    public void testCompression() {
        var stringBuilder = new StringBuilder();

        byte[] compressedBody = WebFramework.compressBodyIfRequested(
                gettysburgAddress.getBytes(StandardCharsets.UTF_8),
                List.of("gzip"),
                stringBuilder, 0);

        byte[] bytes = CompressionUtils.gzipDecompress(compressedBody);
        String body = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(body, gettysburgAddress);
        assertEquals(stringBuilder.toString(), "Content-Encoding: gzip"  + HTTP_CRLF);
    }

    /**
     * If the user sends us an Accept-Encoding with anything else besides
     * gzip, we won't compress.
     */
    @Test
    public void testCompression_EdgeCase_NoGzip() {
        var stringBuilder = new StringBuilder();

        byte[] bytes = WebFramework.compressBodyIfRequested(
                gettysburgAddress.getBytes(StandardCharsets.UTF_8), List.of("deflate"), stringBuilder, 0);

        String body = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(body, gettysburgAddress);
        assertEquals(stringBuilder.toString(), "");
    }

    /**
     * A complete URL, known as the absolute form, is mostly used with
     * GET when connected to a proxy.
     */
    @Test
    public void testAbsoluteForm() throws Exception {
        String startLineString = "GET https://foo.bar.baz HTTP/1.1";
        var startLine = RequestLine.empty(context).extractRequestLine(startLineString);
        var webFramework = new WebFramework(context);

        ThrowingFunction<Request, Response> response = webFramework.findEndpointForThisStartline(startLine);

        assertEquals(response.apply(null), new Response(CODE_400_BAD_REQUEST));
    }


    /**
     * The authority component of a URL, consisting of the domain name
     * and optionally the port (prefixed by a ':'), is called the
     * authority form. It is only used with CONNECT when setting up
     * an HTTP tunnel.
     * <p>
     *     We don't handle HTTP tunnels.
     * </p>
     */
    @Test
    public void testAuthorityComponent() {
        String startLineString = "CONNECT  foo.bar:80 HTTP/1.1";
        var startLine = RequestLine.empty(context).extractRequestLine(startLineString);
        var webFramework = new WebFramework(context);

        ThrowingFunction<Request, Response> response = webFramework.findEndpointForThisStartline(startLine);

        assertTrue(response == null);
    }

    /**
     * The asterisk form, a simple asterisk ('*') is used
     * with OPTIONS, representing the server as a whole. OPTIONS * HTTP/1.1
     */
    @Test
    public void testAsteriskForm() {
        String startLineString = "OPTIONS * HTTP/1.1";
        var startLine = RequestLine.empty(context).extractRequestLine(startLineString);
        var webFramework = new WebFramework(context);

        ThrowingFunction<Request, Response> response = webFramework.findEndpointForThisStartline(startLine);

        assertTrue(response == null);
    }

    /**
     * If processRequest is given a handlerFinder that returns null
     * for the path, we'll return a 404 not found.
     */
    @Test
    public void testNoEndpointFound() throws Exception {
        var webFramework = new WebFramework(context);
        WebFramework.ProcessingResult result;
        try (var sw = new FakeSocketWrapper()) {

            result = webFramework.processRequest(
                    path -> null,
                    sw,
                    new RequestLine(POST,
                            new RequestLine.PathDetails(
                                    "FOO",
                                    "",
                                    Map.of()
                            ),
                            ONE_DOT_ONE,
                            "POST /FOO HTTP/1.1",
                            context
                    ),
                    null,
                    null);
        }

        assertEquals(result.resultingResponse(), new Response(CODE_404_NOT_FOUND));
        assertEquals(result.clientRequest().requestLine().toString(), "StartLine{method=POST, pathDetails=PathDetails[isolatedPath=FOO, rawQueryString=, queryString={}], version=ONE_DOT_ONE, rawValue='POST /FOO HTTP/1.1'}");
        assertEquals(result.clientRequest().remoteRequester(), "tester");
    }

    /**
     * If a failure takes place during processing of the business code, a
     * random integer will be generated and returned in the 500 to the browser,
     * and also to the logs.  This correlation helps the developer debug the situation.
     */
    @Test
    public void testExceptionThrownWhileProcessing() throws Exception {
        var webFramework = new WebFramework(context);
        ThrowingFunction<RequestLine, ThrowingFunction<Request, Response>> handlerFinder = path -> request -> {
            throw new RuntimeException("just for testing error handling - no worries");
        };
        WebFramework.ProcessingResult result;
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {
            result = webFramework.processRequest(handlerFinder, sw, null, null, null);
        }
        assertEquals(result.resultingResponse().statusCode(), CODE_500_INTERNAL_SERVER_ERROR);
        String body = new String(result.resultingResponse().body());
        String errorCode = RegexUtils.find("Server error: (?<errorcode>.*)$", body, "errorcode");
        String errorMessage = logger.findFirstMessageThatContains(errorCode, 7);

        assertTrue(errorMessage.contains("Code: "+errorCode+". Error: java.lang.RuntimeException: just for testing error handling - no worries"));
    }

    /**
     * This method handles actually sending the data on the socket.
     * If we get a HEAD request, we send the headers but no body.
     */
    @Test
    public void testSendingResponse_Head() throws IOException {
        var webFramework = new WebFramework(context);
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {
            var preparedResponse = new PreparedResponse("fake status line and headers", new byte[0]);
            var processingResult = new WebFramework.ProcessingResult(
                    new Request(
                            null,
                            new RequestLine(HEAD,
                                    new RequestLine.PathDetails("fake testing path", null, null),
                                    null,
                                    null,
                                    context),
                            null,
                            null),
                    null);
            webFramework.sendResponse(sw, preparedResponse, processingResult);
        }

        String msg = logger.findFirstMessageThatContains("is requesting HEAD");
        assertEquals(msg, "client null is requesting HEAD for fake testing path.  Excluding body from response");
    }

    @Test
    public void testPreparedResponseString() {
        var preparedResponse = new PreparedResponse("fake status line and headers", new byte[0]);
        assertEquals(preparedResponse.toString(), "PreparedResponse{statusLineAndHeaders='fake status line and headers', body=[]}");
    }

    /**
     * If the client has made a connection but has sent an empty string in
     * the start line spot, there's nothing for us to do, so bail out with
     * an exception.  This is going to be common, because browsers make
     * over-eager connections anticipating a user request, and because of
     * keep-alive connections that end up being unused.
     */
    @Test
    public void testGettingProcessedStartLine_EdgeCase_EmptyStartLine() {
        var webFramework = new WebFramework(context);
        RequestLine result;
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {

            result = webFramework.getProcessedRequestLine(sw, "");
        }
        assertEquals(result, RequestLine.empty(context));
    }

    @Test
    public void testGettingProcessedStartLine_EdgeCase_InvalidStartLine() {
        var webFramework = new WebFramework(context);
        RequestLine result;
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {

            result = webFramework.getProcessedRequestLine(sw, "FOO FOO the FOO");
        }
        assertEquals(result, RequestLine.empty(context));
    }

    /**
     * If the client requests a path we deem suspicious, we
     * throw an exception that will put them into the brig
     * <p>
     *     Note: the ".env" suspicious path is set in the initialization method
     *     for this test class.
     * </p>
     */
    @Test
    public void testCheckIfSuspiciousPath() {
        var webFramework = new WebFramework(context);
        ForbiddenUseException ex;
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {

            ex = assertThrows(ForbiddenUseException.class, () -> webFramework.checkIfSuspiciousPath(
                    sw,
                    new RequestLine(GET,
                            new RequestLine.PathDetails(".env", null, null),
                            ONE_DOT_ONE,
                            null,
                            context)
            ));
        }
        assertEquals(ex.getMessage(), "tester is looking for a vulnerability, for this: .env");
    }

    /**
     * When we start handling the request from a client, right at
     * the onset we check to see if this is a client we recognize
     * in our list of attackers.  if so, we throw a ForbiddenUseException,
     * which will prevent further processing and also re-add this client to
     * the brig.  As long as they are sending requests, we're going
     * to reset their time in the brig.
     */
    @Test
    public void testDumpIfAttacker() {
        var webFramework = new WebFramework(context);
        ForbiddenUseException ex;
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {
            TheBrig theBrig = new TheBrig(context);
            String attacker = "I_am_attacker";
            theBrig.sendToJail(attacker + "_vuln_seeking", 500);
            sw.getRemoteAddrAction = () -> attacker;

            ex = assertThrows(ForbiddenUseException.class, () -> webFramework.dumpIfAttacker(sw, theBrig));
        }

        assertEquals(ex.getMessage(), "closing the socket on I_am_attacker due to being found in the brig");
    }

    /**
     * If the http version is not 1.1 or 1.0, it's not keep alive
     */
    @Test
    public void testDetermineIfKeepAlive_EdgeCase_HttpVersionNone() {
        RequestLine requestLine = new RequestLine(GET, RequestLine.PathDetails.empty, HttpVersion.NONE, "", context);
        Headers headers = new Headers(List.of("connection: keep-alive"), context);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertFalse(isKeepAlive);
    }

    /**
     * If an HTTP request is version 1.1, the default is to set them as keep-alive
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotOne() {
        RequestLine requestLine = new RequestLine(GET, RequestLine.PathDetails.empty, ONE_DOT_ONE, "", context);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, Headers.make(context), logger);
        assertTrue(isKeepAlive);
    }

    /**
     * If an HTTP request is version 1.0, the keep-alive header has to be explicit
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotZero() {
        RequestLine requestLine = new RequestLine(GET, RequestLine.PathDetails.empty, HttpVersion.ONE_DOT_ZERO, "", context);
        Headers headers = new Headers(List.of("connection: keep-alive"), context);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertTrue(isKeepAlive);
    }

    /**
     * If an HTTP request is version 1.0, the keep-alive header has to be explicit
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotZero_NoHeader() {
        RequestLine requestLine = new RequestLine(GET, RequestLine.PathDetails.empty, HttpVersion.ONE_DOT_ZERO, "", context);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, Headers.make(context), logger);
        assertFalse(isKeepAlive);
    }

    /**
     * If the client sends connection: close, we won't do keep-alive
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotZero_ConnectionClose() {
        RequestLine requestLine = new RequestLine(GET, RequestLine.PathDetails.empty, HttpVersion.ONE_DOT_ZERO, "", context);
        Headers headers = new Headers(List.of("connection: close"), context);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertFalse(isKeepAlive);
    }

    /**
     * If the client sends connection: close, we won't do keep-alive
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotOne_ConnectionClose() {
        RequestLine requestLine = new RequestLine(GET, RequestLine.PathDetails.empty, ONE_DOT_ONE, "", context);
        Headers headers = new Headers(List.of("connection: close"), context);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertFalse(isKeepAlive);
    }

    /**
     * Because the code is written to facilitate access for testing,
     * there have to be checks on whether the objects are non-null before
     * running {@link WebFramework#dumpIfAttacker(ISocketWrapper, ITheBrig)}.
     */
    @Test
    public void test_dumpAttackerNullChecks_NullBrig() {
        var webFramework = new WebFramework(context);
        var fullSystem = new FullSystem(context);
        assertFalse(webFramework.dumpIfAttacker(null, fullSystem));
    }

    @Test
    public void test_dumpAttackerNullChecks_NullFullSystem() {
        var webFramework = new WebFramework(context);
        assertFalse(webFramework.dumpIfAttacker(null, (FullSystem) null));
    }

    /**
     * Because this trace logging can get a little tricky..
     */
    @Test
    public void test_GettingBodyStringForTraceLogging() {
        assertEquals(WebFramework.getBodyStringForTraceLog("a"), "The body is: a");
        assertEquals(WebFramework.getBodyStringForTraceLog(""), "The body is: ");
        assertEquals(WebFramework.getBodyStringForTraceLog("abc"), "The body is: abc");
        assertEquals(WebFramework.getBodyStringForTraceLog("a".repeat(49)), "The body is: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertEquals(WebFramework.getBodyStringForTraceLog("a".repeat(50)), "The body is: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertEquals(WebFramework.getBodyStringForTraceLog("a".repeat(51)), "The body is: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertEquals(WebFramework.getBodyStringForTraceLog("a".repeat(52)), "The body is: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertEquals(WebFramework.getBodyStringForTraceLog(null), "The body was null");
    }

}
