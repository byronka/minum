package com.renomad.minum.web;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.TheBrig;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFailureException;
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
import static com.renomad.minum.web.HttpVersion.ONE_DOT_ONE;
import static com.renomad.minum.web.RequestLine.Method.*;
import static com.renomad.minum.web.RequestLine.startLineRegex;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

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
    static private TestLogger logger;
    private static String gettysburgAddress;
    private static Headers defaultHeader = new Headers(List.of());

    /**
     * The length of time, in milliseconds, we will wait for the server to close
     * before letting computation continue.
     * <br>
     * Otherwise, for some operating systems (Windows), there will be a conflict when the
     * next server bind occurs.
     */
    static int SERVER_CLOSE_WAIT_TIME = 30;
    private static FileUtils fileUtils;

    @BeforeClass
    public static void setUpClass() throws IOException {
        var properties = new Properties();
        properties.setProperty("SERVER_PORT", "7777");
        properties.setProperty("SUSPICIOUS_PATHS", ".env");
        properties.setProperty("IS_THE_BRIG_ENABLED", "true");
        properties.setProperty("SOCKET_TIMEOUT_MILLIS", "300");
        context = buildTestingContext("unit_tests",properties);
        es = context.getExecutorService();
        inputStreamUtils = new InputStreamUtils(context.getConstants().maxReadLineSizeBytes);
        logger = (TestLogger)context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
        gettysburgAddress = Files.readString(Path.of("src/test/resources/gettysburg_address.txt"));
    }


    @AfterClass
    public static void tearDownClass() {
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        shutdownTestingContext(context);
    }

    /**
     * What happens if we throw an exception in a thread?
     */
    @Test
    public void test_ThrowingExceptionInThread() {
        var future = es.submit(() -> {
            try {
                throw new RuntimeException("No worries folks, just testing the exception handling, from test \"What happens if we throw an exception in a thread\"");
            } catch (Exception ex) {
                logger.logDebug(ex::getMessage);
            }
        });
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
        assertThrows(TestFailureException.class, future::get);
        assertTrue(logger.doesMessageExist("No worries folks, just testing the exception handling", 8));
    }

    /**
     * This class belongs to the test below, "starting server with a handler part 2"
     * This represents the shape an endpoint could take. It's given a request object,
     * we presume it has everything is needs to do its work (query strings on the path,
     * header values, body contents, all that stuff), and it replies with a response
     * object that gets ferried along to the client.
     */
    static class Summation {
        static IResponse addTwoNumbers(IRequest r) {
            int aValue = Integer.parseInt(r.getRequestLine().queryString().get("a"));
            int bValue = Integer.parseInt(r.getRequestLine().getPathDetails().getQueryString().get("b"));
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
        var webEngine = new WebEngine(context, wf);

        wf.registerPath(GET, "add_two_numbers", Summation::addTwoNumbers);
        try (IServer primaryServer = webEngine.startServer()) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /add_two_numbers?a=42&b=44 HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("");

                    StatusLine statusLine = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));

                    assertEquals(statusLine.rawValue(), "HTTP/1.1 200 OK");

                    List<String> allHeaders = Headers.getAllHeaders(is, inputStreamUtils);
                    Headers hi = new Headers(allHeaders);

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
        RequestLine sl = RequestLine.EMPTY.extractRequestLine("POST /something HTTP/1.0");
        assertEquals(sl.getMethod(), POST);
    }

    @Test
    public void test_StartLine_EmptyPath() {
        RequestLine sl = RequestLine.EMPTY.extractRequestLine("GET / HTTP/1.1");
        assertEquals(sl.getMethod(), GET);
        assertEquals(sl.getPathDetails().getIsolatedPath(), "");
    }

    @Test
    public void test_StartLine_DeeperPath() {
        RequestLine sl = RequestLine.EMPTY.extractRequestLine("GET /foo/bar/baz HTTP/1.1");
        assertEquals(sl.getMethod(), GET);
        assertEquals(sl.getPathDetails().getIsolatedPath(), "foo/bar/baz");
    }

    @Test
    public void test_StartLine_DeeperPath2() {
        RequestLine sl = RequestLine.EMPTY.extractRequestLine("GET /foo/bar/baz/ HTTP/1.1");
        assertEquals(sl.getMethod(), GET);
        assertEquals(sl.getPathDetails().getIsolatedPath(), "foo/bar/baz/");
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
                assertEquals(RequestLine.EMPTY.extractRequestLine(s), RequestLine.EMPTY);
            }
            assertThrows(InvariantException.class, () -> RequestLine.EMPTY.extractRequestLine(null));
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
        assertThrows(WebServerException.class,
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
        byte[] bytes = "value_a=123&value_b=456".getBytes(StandardCharsets.US_ASCII);
        final var result = new BodyProcessor(context).parseUrlEncodedForm(new ByteArrayInputStream(bytes), bytes.length);
        assertEquals(expected.get("value_a"), result.asString("value_a"));
        assertEquals(expected.get("value_b"), result.asString("value_b"));
    }

    @Test
    public void test_ParseForm_EdgeCase_BlankKey() {
        byte[] bytes = "=123".getBytes(StandardCharsets.US_ASCII);
        new BodyProcessor(context).parseUrlEncodedForm(new ByteArrayInputStream(bytes), bytes.length);
        assertTrue(logger.doesMessageExist("Unable to parse this body. no key found during parsing"));
    }

    @Test
    public void test_ParseForm_EdgeCase_DuplicateKey() {
        byte[] bytes = "a=123&a=123".getBytes(StandardCharsets.US_ASCII);
        new BodyProcessor(context).parseUrlEncodedForm(new ByteArrayInputStream(bytes), bytes.length);
        var logs = logger.findFirstMessageThatContains("key (a) was duplicated in the post body - previous version was 123 and recent data was 123");
        assertTrue(!logs.isBlank());
    }

    @Test
    public void test_ParseForm_EdgeCase_EmptyValue() {
        byte[] bytes = "mykey=".getBytes(StandardCharsets.US_ASCII);
        final var result = new BodyProcessor(context).parseUrlEncodedForm(new ByteArrayInputStream(bytes), bytes.length);
        assertEquals(result.asString("mykey"), "");
    }

    @Test
    public void test_ParseForm_EdgeCase_NullValue() {
        byte[] bytes = "mykey=%NULL%".getBytes(StandardCharsets.US_ASCII);
        final var result2 = new BodyProcessor(context).parseUrlEncodedForm(new ByteArrayInputStream(bytes), bytes.length);
        assertEquals(result2.asString("mykey"), "");
    }

    @Test
    public void test_ParseForm_Empty() {
        byte[] bytes = "".getBytes(StandardCharsets.US_ASCII);
        final var result = new BodyProcessor(context).parseUrlEncodedForm(new ByteArrayInputStream(bytes), bytes.length);
        assertEquals(result, Body.EMPTY);
    }

    @Test
    public void test_ParseForm_MoreRealisticCase() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        try (var primaryServer = webEngine.startServer()) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (var client = webEngine.startClient(socket)) {
                    wf.registerPath(
                            POST,
                            "some_post_endpoint",
                            request -> Response.htmlOk(request.getBody().asString("value_a"))
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
                    client.send(postedData);

                    // the server will respond to us.  Check everything is legit.
                    StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    List<String> allHeaders = Headers.getAllHeaders(client.getInputStream(), inputStreamUtils);
                    Headers hi = new Headers(allHeaders);
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
        var webEngine = new WebEngine(context, wf);
        try (var primaryServer = webEngine.startServer()) {
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

    @Test
    public void test_MultiPartForm_NoContentDisposition() {
        byte[] multiPartData = makeTestMultiPartDataNoContentDisposition();
        var bp = new BodyProcessor(context);

        bp.extractBodyFromInputStream(multiPartData.length, "multipart/form-data; boundary=i_am_a_boundary", new ByteArrayInputStream(multiPartData));
        assertTrue(logger.doesMessageExist("Unable to parse this body. returning what we have so far.  Exception message: Error: no Content-Disposition header on partition in Multipart/form data"));
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

        Body result = bp.extractBodyFromInputStream(multiPartData.length, "multipart/form-data; boundary=i_am_a_boundary", new ByteArrayInputStream(multiPartData));

        Partition text1Partition = result.getPartitionByName("text1").getFirst();
        assertEquals(text1Partition.getContentAsString(), "I am a value that is text");
        assertEquals(text1Partition.getHeaders().valueByKey("content-type"), List.of("text/plain"));
        assertEquals(text1Partition.getHeaders().valueByKey("content-disposition"), List.of("form-data; name=\"text1\""));
        assertEquals(text1Partition.toString(), "Partition{headers=Headers{headerStrings=[Content-Type: text/plain, Content-Disposition: form-data; name=\"text1\"]}, contentDisposition=ContentDisposition{name='text1', filename=''}}");
        assertEquals(text1Partition.getContentDisposition().toString(), "ContentDisposition{name='text1', filename=''}");
        Partition imagePartition = result.getPartitionByName("image_uploads").getFirst();
        assertEqualByteArray(imagePartition.getContent(), new byte[]{1, 2, 3});
        assertEquals(imagePartition.toString(), "Partition{headers=Headers{headerStrings=[Content-Type: application/octet-stream, Content-Disposition: form-data; name=\"image_uploads\"; filename=\"photo_preview.jpg\"]}, contentDisposition=ContentDisposition{name='image_uploads', filename='photo_preview.jpg'}}");
        assertEquals(imagePartition.getContentDisposition().toString(), "ContentDisposition{name='image_uploads', filename='photo_preview.jpg'}");
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

        final ThrowingFunction<IRequest, IResponse> testHandler = r -> {
            byte[] imageUploads = r.getBody().getPartitionByName("image_uploads").getFirst().getContent();
            if (r.getBody().getPartitionByName("text1").getFirst().getContentAsString().equals("I am a value that is text") &&
                    imageUploads.length == 3 &&
                    imageUploads[0] == 1 &&
                    imageUploads[1] == 2 &&
                    imageUploads[2] == 3
            ) {
                return Response.htmlOk("<p>everything was ok</p>");
            } else {
              return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            }
        };

        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        wf.registerPath(POST, "some_endpoint", testHandler);
        try (IServer primaryServer = webEngine.startServer()) {
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

        final ThrowingFunction<IRequest, IResponse> testHandler = r -> {
            byte[] kittyPic = Files.readAllBytes(Path.of("src/test/resources/kitty.jpg"));
            if (r.getBody().getPartitionByName("text1").getFirst().getContentAsString().equals("I am a value that is text") &&
                    isEqualByteArray(r.getBody().getPartitionByName("image_uploads").getFirst().getContent(), new byte[]{1,2,3}) &&
                    isEqualByteArray(r.getBody().getPartitionByName("kitty1").getFirst().getContent(), kittyPic) &&
                    isEqualByteArray(r.getBody().getPartitionByName("kitty2").getFirst().getContent(), kittyPic)
            ) {
                return Response.htmlOk("<p>everything was ok</p>");
            } else {
              return Response.buildLeanResponse(CODE_404_NOT_FOUND);
            }
        };

        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        wf.registerPath(POST, "some_endpoint", testHandler);
        try (IServer primaryServer = webEngine.startServer()) {
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


    /**
     * Similar to {@link #test_MultiPartForm_HappyPath_MoreImages()} but uses a multipart-form
     * data with an input having the "multiple" option set, meaning that the data will be transmitted with
     * the same input name multiple times but differing filenames.  For example:
     * <pre>
     * POST http://localhost:8080/upload HTTP/1.1
     * Host: localhost:8080
     * Connection: keep-alive
     * Content-Length: 585
     * Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryOvAP22uXpkpUKh5g
     *
     * ------WebKitFormBoundaryOvAP22uXpkpUKh5g
     * Content-Disposition: form-data; name="image_uploads"; filename="kitty.jpg"
     * Content-Type: image/jpeg
     *
     * ...binary here...
     * ------WebKitFormBoundaryOvAP22uXpkpUKh5g
     * Content-Disposition: form-data; name="image_uploads"; filename="kitty_2.jpg"
     * Content-Type: image/jpeg
     *
     * ...binary here...
     * ------WebKitFormBoundaryOvAP22uXpkpUKh5g
     * Content-Disposition: form-data; name="short_description"
     *
     * asfd
     * ------WebKitFormBoundaryOvAP22uXpkpUKh5g
     * Content-Disposition: form-data; name="long_description"
     *
     *
     * ------WebKitFormBoundaryOvAP22uXpkpUKh5g--
     * </pre>
     */
    @Test
    public void test_MultiPartForm_HappyPath_MultipleImages() throws Exception {
        byte[] multiPartData = makeTestMultiPartData_InputWithMultipleOption();

        // This is the core of the test - here's where we'll process receiving a multipart data

        final ThrowingFunction<IRequest, IResponse> testHandler = r -> {
            byte[] kittyPic = Files.readAllBytes(Path.of("src/test/resources/kitty.jpg"));
            List<Partition> kitty = r.getBody().getPartitionByName("kitty");
            if (r.getBody().getPartitionByName("text1").getFirst().getContentAsString().equals("I am a value that is text") &&

                    isEqualByteArray(r.getBody().getPartitionByName("image_uploads").getFirst().getContent(), new byte[]{1,2,3}) &&
                    isEqualByteArray(kitty.get(0).getContent(), kittyPic) &&
                    isEqualByteArray(kitty.get(1).getContent(), kittyPic)
            ) {
                return Response.htmlOk("<p>everything was ok</p>");
            } else {
                return Response.buildLeanResponse(CODE_500_INTERNAL_SERVER_ERROR);
            }
        };

        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        wf.registerPath(POST, "some_endpoint", testHandler);
        try (IServer primaryServer = webEngine.startServer()) {
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

    /**
     * the length is known in advance, so content-length is sent to the client.
     */
    @Test
    public void test_StreamingResponse_KnownContentLength() throws Exception {
        String dataToSend = "I am the data to send";

        // This is the core of the test - here's where we'll process receiving a multipart data

        final ThrowingFunction<IRequest, IResponse> testHandler = r -> {
            return Response.buildStreamingResponse(
                    CODE_200_OK,
                    Map.of("Content-Type", "text/plain"),
                    socketWrapper -> socketWrapper.send(dataToSend),
                    dataToSend.length());
        };

        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        wf.registerPath(GET, "some_endpoint", testHandler);
        try (IServer primaryServer = webEngine.startServer()) {
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
                    List<String> allHeaders = Headers.getAllHeaders(is, inputStreamUtils);
                    Headers headers1 = new Headers(allHeaders);
                    int length = Integer.parseInt(headers1.valueByKey("content-length").getFirst());

                    byte[] bytes = inputStreamUtils.read(length, is);
                    String s = new String(bytes, StandardCharsets.UTF_8);
                    assertEquals("I am the data to send", s);
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
    }

    @Test
    public void test_Headers_Multiple() {
        Headers headers = new Headers(List.of("foo: a", "foo: b"));
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
        var startLine = RequestLine.EMPTY.extractRequestLine(startLineString);

        // instantiate a web framework to do the processing.  We're TDD'ing some new
        // code inside this.
        var webFramework = new WebFramework(context);

        // register a path to handle this pattern.  Wishful-thinking for the win.
        webFramework.registerPartialPath(GET, ".well-known/acme-challenge", request -> {
            String path = request.getRequestLine().getPathDetails().getIsolatedPath();
            return Response.htmlOk("value was " + path);
        });

        // our code should properly find a handler for this endpoint
        var endpoint = webFramework.findEndpointForThisStartline(startLine, defaultHeader);

        // now we create a whole request to stuff into this handler. We only
        // care about the request line.
        IRequest request = new Request(
                new Headers(List.of()),
                startLine,
                "",
                new FakeSocketWrapper(),
                new BodyProcessor(context));

        // when we run that handler against the request, it works.
        IResponse response = endpoint.apply(request);
        assertEquals(StringUtils.byteArrayToString(response.getBody()), "value was .well-known/acme-challenge/foobar");
    }

    /**
     * Playing a bit with some of keep-alive behavior
     */
    @Test
    public void test_KeepAlive_Http_1_0() throws Exception {

        final ThrowingFunction<IRequest, IResponse> testHandler = r -> Response.htmlOk("looking good!");

        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        wf.registerPath(GET, "some_endpoint", testHandler);
        try (IServer primaryServer = webEngine.startServer()) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                try (ISocketWrapper client = webEngine.startClient(socket)) {
                    InputStream is = client.getInputStream();

                    // send a GET request
                    client.sendHttpLine("GET /some_endpoint HTTP/1.0");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: keep-alive");
                    client.sendHttpLine("");

                    StatusLine statusLine1 = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine1.status(), CODE_200_OK);
                    List<String> allHeaders = Headers.getAllHeaders(is, inputStreamUtils);
                    Headers headers1 = new Headers(allHeaders);
                    assertTrue(headers1.valueByKey("keep-alive").contains("timeout=3"));
                    new BodyProcessor(context).extractData(is, headers1);

                    // send another GET request, but closing it
                    client.sendHttpLine("GET /some_endpoint HTTP/1.1");
                    client.sendHttpLine("Host: localhost:8080");
                    client.sendHttpLine("Connection: close");
                    client.sendHttpLine("");

                    StatusLine statusLine2 = StatusLine.extractStatusLine(inputStreamUtils.readLine(is));
                    assertEquals(statusLine2.status(), CODE_200_OK);
                    List<String> allHeaders2 = Headers.getAllHeaders(is, inputStreamUtils);
                    Headers headers2 = new Headers(allHeaders2);
                    assertTrue(headers2.valueByKey("keep-alive") == null);
                }
            }
        }

        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);

        try (IServer primaryServer = webEngine.startServer()) {
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
                    List<String> allHeaders = Headers.getAllHeaders(is, inputStreamUtils);
                    Headers headers1 = new Headers(allHeaders);
                    assertTrue(headers1.valueByKey("keep-alive").contains("timeout=3"));
                }
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);

    }

    @Test
    public void test_InvalidRequestLine() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        try (IServer primaryServer = webEngine.startServer()) {
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
            WebEngine webEngine = new WebEngine(context, null);
            assertThrows(WebServerException.class, () -> webEngine.startServer());
            MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
        } finally {
            shutdownTestingContext(context);
        }
    }

    @Test
    public void test_IsThereABody_ContentType() {
        // content-type: foo is illegitimate, but it will cause the system to look closer
        var headers1 = new Headers(List.of("content-type: foo"));
        assertFalse(WebFramework.isThereIsABody(headers1));
    }

    @Test
    public void test_IsThereABody_TransferEncodingFoo() {
        // transfer-encoding: foo is illegitimate, it will look for chunked but won't find it.
        var headers2 = new Headers(List.of("content-type: foo", "transfer-encoding: foo"));
        assertFalse(WebFramework.isThereIsABody(headers2));
    }

    @Test
    public void test_IsThereABody_TransferEncodingChunked() {
        // transfer-encoding: chunked is acceptable, it will return true here
        var headers3 = new Headers(List.of("content-type: foo", "transfer-encoding: chunked"));
        assertTrue(WebFramework.isThereIsABody(headers3));
    }

    /**
     * a situation where nothing was registered to the list of partial paths
     */
    @Test
    public void test_PartialMatch_NothingRegistered() {
        var webFramework = new WebFramework(context, default_zdt);

        var startLine = new RequestLine(GET, new PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", logger);
        assertTrue(webFramework.findHandlerByPartialMatch(startLine) == null);
    }

    /**
     * a complete match - not merely a partial match.  mypath == mypath
     */
    @Test
    public void test_PartialMatch_PerfectMatch() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<IRequest, IResponse> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);

        var startLine = new RequestLine(GET, new PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", logger);
        assertEquals(webFramework.findHandlerByPartialMatch(startLine), helloHandler);
    }

    @Test
    public void test_PartialMatch_DoesNotMatch() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<IRequest, IResponse> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);

        var startLine = new RequestLine(GET, new PathDetails("mypa_DOES_NOT_MATCH", "", Map.of()), ONE_DOT_ONE, "", logger);
        assertTrue(webFramework.findHandlerByPartialMatch(startLine) == null);
    }

    @Test
    public void test_PartialMatch_DifferentMethod() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<IRequest, IResponse> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);

        var startLine = new RequestLine(POST, new PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", logger);
        assertTrue(webFramework.findHandlerByPartialMatch(startLine) == null);
    }

    @Test
    public void test_PartialMatch_MatchTooMuch() {
        var webFramework = new WebFramework(context, default_zdt);
        ThrowingFunction<IRequest, IResponse> helloHandler = request -> Response.htmlOk("hello");

        webFramework.registerPartialPath(GET, "mypath", helloHandler);
        webFramework.registerPartialPath(GET, "m", helloHandler);

        var startLine = new RequestLine(GET, new PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", logger);
        assertEquals(webFramework.findHandlerByPartialMatch(startLine), helloHandler);
    }

    /**
     * if pathDetails is null, we'll get an empty hashmap
     */
    @Test
    public void test_QueryString_NullPathdetails() {
        var startLine = new RequestLine(GET, null, ONE_DOT_ONE, "", logger);
        assertEquals(startLine.queryString(), new HashMap<>());
    }

    @Test
    public void test_QueryString_NullQueryString() {
        var startLine = new RequestLine(GET, new PathDetails("mypath", "", null), ONE_DOT_ONE, "", logger);
        assertEquals(startLine.queryString(), new HashMap<>());
    }

    @Test
    public void test_QueryString_EmptyQueryString() {
        var startLIne = new RequestLine(GET, new PathDetails("mypath", "", Map.of()), ONE_DOT_ONE, "", logger);
        assertEquals(startLIne.queryString(), new HashMap<>());
    }

    /**
     * The hash function we generated ought to get some use.
     * We'll pop StartLine in a hashmap to test it.
     */
    @Test
    public void test_StartLine_Hashing() {
        var startLines = Map.of(
                new RequestLine(GET, new PathDetails("foo", "", Map.of()), ONE_DOT_ONE, "", logger),   "foo",
                new RequestLine(GET, new PathDetails("bar", "", Map.of()), ONE_DOT_ONE, "", logger),   "bar",
                new RequestLine(GET, new PathDetails("baz", "", Map.of()), ONE_DOT_ONE, "", logger),   "baz"
        );
        assertEquals(startLines.get(new RequestLine(GET, new PathDetails("bar", "", Map.of()), ONE_DOT_ONE, "", logger)), "bar");
    }

    /**
     * test when there's more query string key-value pairs than allowed by MAX_QUERY_STRING_KEYS_COUNT
     */
    @Test
    public void test_ExtractMapFromQueryString_TooManyPairs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RequestLine.MAX_QUERY_STRING_KEYS_COUNT + 2; i++) {
                sb.append(String.format("foo%d=bar%d&", i, i));
        }
        RequestLine requestLine = RequestLine.EMPTY;
        assertThrows(ForbiddenUseException.class, () -> requestLine.extractMapFromQueryString(sb.toString()));
    }

    /**
     * if there is no equals sign in the query string
     */
    @Test
    public void test_ExtractMapFromQueryString_NoEqualsSign() {
        RequestLine requestLine = new RequestLine(NONE, PathDetails.empty, HttpVersion.NONE, "", logger);
        var result = requestLine.extractMapFromQueryString("foo");
        assertEquals(result, Map.of());
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

    /**
     * This creates a multipart request resembling what is generated when the user
     * chooses the "multiple" option on a file input element.
     */
    private static byte[] makeTestMultiPartData_InputWithMultipleOption() {
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
                        Content-Disposition: form-data; name="kitty"; filename="kitty1.jpg"\r
                        \r
                        """.getBytes(StandardCharsets.UTF_8));
            baos.write(kittyBytes);
            baos.write("""
                        \r
                        --i_am_a_boundary\r
                        Content-Type: application/octet-stream\r
                        Content-Disposition: form-data; name="kitty"; filename="kitty2.jpg"\r
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
        byte[] read = inputStreamUtils.read(length, is);
        return StringUtils.byteArrayToString(read);
    }

    /**
     * If the user sends us an Accept-Encoding with anything else besides
     * gzip, we won't compress.
     */
    @Test
    public void testCompression_EdgeCase_NoGzip() throws IOException {
        var stringBuilder = new StringBuilder();
        Response response = (Response) Response.htmlOk(gettysburgAddress);

        WebFramework.compressBodyIfRequested(
                response,
                List.of("deflate"),
                stringBuilder, 0);

        String body = new String(response.getBody(), StandardCharsets.UTF_8);
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
        var startLine = RequestLine.EMPTY.extractRequestLine(startLineString);
        var webFramework = new WebFramework(context);

        ThrowingFunction<IRequest, IResponse> response = webFramework.findEndpointForThisStartline(startLine, defaultHeader);

        assertEquals(response.apply(null), Response.buildLeanResponse(CODE_400_BAD_REQUEST));
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
        var startLine = new RequestLine(NONE, PathDetails.empty, HttpVersion.NONE, "", logger).extractRequestLine(startLineString);
        var webFramework = new WebFramework(context);

        ThrowingFunction<IRequest, IResponse> response = webFramework.findEndpointForThisStartline(startLine, defaultHeader);

        assertTrue(response == null);
    }

    /**
     * The asterisk form, a simple asterisk ('*') is used
     * with OPTIONS, representing the server as a whole. OPTIONS * HTTP/1.1
     */
    @Test
    public void testAsteriskForm() {
        String startLineString = "OPTIONS * HTTP/1.1";
        var startLine = RequestLine.EMPTY.extractRequestLine(startLineString);
        var webFramework = new WebFramework(context);

        ThrowingFunction<IRequest, IResponse> response = webFramework.findEndpointForThisStartline(startLine, defaultHeader);

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
                    sw,
                    new RequestLine(POST,
                            new PathDetails(
                                    "FOO",
                                    "",
                                    Map.of()
                            ),
                            ONE_DOT_ONE,
                            "POST /FOO HTTP/1.1",
                            logger
                    ),
                    null);
        }

        assertEquals(result.resultingResponse(), Response.buildLeanResponse(CODE_404_NOT_FOUND));
        assertEquals(result.clientRequest().getRequestLine().toString(), "RequestLine{method=POST, pathDetails=PathDetails{isolatedPath='FOO', rawQueryString='', queryString={}}, version=ONE_DOT_ONE, rawValue='POST /FOO HTTP/1.1', logger=TestLogger using queue: loggerPrinterunit_tests}");
        assertEquals(result.clientRequest().getRemoteRequester(), "tester");
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
        assertEquals(result, RequestLine.EMPTY);
    }

    @Test
    public void testGettingProcessedStartLine_EdgeCase_InvalidStartLine() {
        var webFramework = new WebFramework(context);
        RequestLine result;
        try (FakeSocketWrapper sw = new FakeSocketWrapper()) {

            result = webFramework.getProcessedRequestLine(sw, "FOO FOO the FOO");
        }
        assertEquals(result, RequestLine.EMPTY);
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
                            new PathDetails(".env", null, null),
                            ONE_DOT_ONE,
                            null,
                            logger)
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
        RequestLine requestLine = new RequestLine(GET, PathDetails.empty, HttpVersion.NONE, "", logger);
        Headers headers = new Headers(List.of("connection: keep-alive"));
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertFalse(isKeepAlive);
    }

    /**
     * If an HTTP request is version 1.1, the default is to set them as keep-alive
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotOne() {
        RequestLine requestLine = new RequestLine(GET, PathDetails.empty, ONE_DOT_ONE, "", logger);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, new Headers(List.of()), logger);
        assertTrue(isKeepAlive);
    }

    /**
     * If an HTTP request is version 1.0, the keep-alive header has to be explicit
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotZero() {
        RequestLine requestLine = new RequestLine(GET, PathDetails.empty, HttpVersion.ONE_DOT_ZERO, "", logger);
        Headers headers = new Headers(List.of("connection: keep-alive"));
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertTrue(isKeepAlive);
    }

    /**
     * If an HTTP request is version 1.0, the keep-alive header has to be explicit
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotZero_NoHeader() {
        RequestLine requestLine = new RequestLine(GET, PathDetails.empty, HttpVersion.ONE_DOT_ZERO, "", logger);
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, new Headers(List.of()), logger);
        assertFalse(isKeepAlive);
    }

    /**
     * If the client sends connection: close, we won't do keep-alive
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotZero_ConnectionClose() {
        RequestLine requestLine = new RequestLine(GET, PathDetails.empty, HttpVersion.ONE_DOT_ZERO, "", logger);
        Headers headers = new Headers(List.of("connection: close"));
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(requestLine, headers, logger);
        assertFalse(isKeepAlive);
    }

    /**
     * If the client sends connection: close, we won't do keep-alive
     */
    @Test
    public void testDetermineIfKeepAlive_OneDotOne_ConnectionClose() {
        RequestLine requestLine = new RequestLine(GET, PathDetails.empty, ONE_DOT_ONE, "", logger);
        Headers headers = new Headers(List.of("connection: close"));
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

}
