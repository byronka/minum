package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.MyThread;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.FunctionalTesting.extractStatusLine;
import static com.renomad.minum.web.RequestLine.Method.GET;
import static com.renomad.minum.web.RequestLine.Method.POST;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_200_OK;

/**
 * <p>
 *     Deliberately-failing regression tests.  Each test in this class asserts the
 *     <em>correct</em> (desired) behavior for a defect that exists in the code today,
 *     and therefore fails.  Each test's javadoc names the defect location and describes
 *     what the behavior ought to be.
 * </p>
 * <p>
 *     Note: the surefire configuration in pom.xml sets skipAfterFailureCount to 1, so a
 *     full-class run halts at the first failure.  Run these one at a time, e.g.
 *     {@code ./mvnw surefire:test -Dtest=FindingsWebTests#test_Finding_RangeNotClampedToFileLength}
 * </p>
 */
public class FindingsWebTests {

    private static Context context;
    private static Constants constants;
    private static TestLogger logger;
    private static IInputStreamUtils inputStreamUtils;

    /**
     * how long to wait, in milliseconds, after closing a server, so that the
     * operating system has time to release the port before the next test binds it.
     */
    private static final int SERVER_CLOSE_WAIT_TIME = 50;

    private static final java.time.ZonedDateTime default_zdt =
            java.time.ZonedDateTime.of(2022, 1, 4, 9, 25, 0, 0, java.time.ZoneId.of("UTC"));

    @BeforeClass
    public static void init() {
        context = buildTestingContext("FindingsWebTests");
        logger = (TestLogger) context.getLogger();
        constants = context.getConstants();
        inputStreamUtils = new InputStreamUtils(constants.maxReadLineSizeBytes);
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /*
     * ==========================================================================
     * FINDING 1: request smuggling via Transfer-Encoding: chunked
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> HTTP request smuggling through {@code Transfer-Encoding: chunked}.</p>
     * <p><b>Defect:</b> {@code WebFramework.java:385} - {@code determineIfKeepAlive} only guards
     * against an unread body when a {@code content-length} header was present
     * ({@code request.getHeaders().contentLength() >= 0}).  A request that declares
     * {@code Transfer-Encoding: chunked} has no content-length, so the guard never fires; the
     * body is left unread in the socket and keep-alive stays on.  The framework then reads the
     * unconsumed body as if it were the next request on the connection.</p>
     * <p><b>Correct behavior:</b> Minum does not implement chunked request decoding, so a request
     * with {@code Transfer-Encoding: chunked} must either be rejected, or at minimum must close
     * the connection afterwards, so that the unread body can never be interpreted as a second,
     * smuggled request.</p>
     */
    @Test
    public void test_Finding_ChunkedRequestSmuggling() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        // an endpoint that does NOT read the body
        wf.registerPath(POST, "noop", request -> Response.htmlOk("NOOP_OK"));
        // an endpoint that must NOT be reachable by the smuggled request
        wf.registerPath(GET, "smuggled", request -> Response.htmlOk("SMUGGLED_MARKER_XYZ"));

        String allBytes;
        try (IServer primaryServer = webEngine.startServer()) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                socket.setSoTimeout(1500);
                OutputStream os = socket.getOutputStream();
                // ONE raw byte stream: a chunked POST whose "body" is a complete second request.
                String rawRequest =
                        "POST /noop HTTP/1.1\r\n" +
                        "Host: x\r\n" +
                        "Transfer-Encoding: chunked\r\n" +
                        "\r\n" +
                        "GET /smuggled HTTP/1.1\r\n" +
                        "Host: x\r\n" +
                        "\r\n";
                os.write(rawRequest.getBytes(StandardCharsets.US_ASCII));
                os.flush();

                allBytes = readUntilTimeoutOrClose(socket.getInputStream());
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);

        assertTrue(allBytes.contains("NOOP_OK"),
                "Sanity check: the server should have answered the POST /noop request.  Received: " + allBytes);
        assertFalse(allBytes.contains("SMUGGLED_MARKER_XYZ"),
                "SECURITY: the bytes after the headers of a Transfer-Encoding: chunked request are the request BODY, " +
                        "and must never be executed as a second request.  The server should have rejected the chunked " +
                        "request or closed the connection instead of replying to the smuggled 'GET /smuggled'.  " +
                        "Full conversation received was:\n" + allBytes);
    }

    /**
     * <p><b>Finding:</b> same defect as {@link #test_Finding_ChunkedRequestSmuggling()}, proven at
     * the unit level, which is the more maintainable proof.</p>
     * <p><b>Defect:</b> {@code WebFramework.java:385} - the lingering-unread-body check is
     * {@code request.getHeaders().contentLength() >= 0}, and {@code Headers.contentLength()}
     * returns -1 when there is no content-length header.  A chunked request has a body but no
     * content-length, so keep-alive is (wrongly) left on.</p>
     * <p><b>Correct behavior:</b> when the request declares {@code Transfer-Encoding: chunked}
     * and the handler did not read the body, {@code determineIfKeepAlive} must return false.</p>
     */
    @Test
    public void test_Finding_ChunkedRequestSmuggling_DetermineIfKeepAlive() {
        RequestLine requestLine = new RequestLine(POST, PathDetails.empty, HttpVersion.ONE_DOT_ONE, "", logger);
        Headers headers = new Headers(List.of("Transfer-Encoding: chunked"));
        IRequest myRequest = makeMinimalRequest(requestLine, headers);

        // the "false" here means the endpoint never accessed the body
        boolean isKeepAlive = WebFramework.determineIfKeepAlive(myRequest, logger, false);

        assertFalse(isKeepAlive,
                "A request with 'Transfer-Encoding: chunked' has an unread body that the server does not know " +
                        "how to skip over, so keep-alive must be turned off.  Otherwise the unread body bytes get " +
                        "parsed as the next request on the connection (request smuggling).");
    }

    /*
     * ==========================================================================
     * FINDING 2: Range not clamped to the length of the file
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> the {@code Range} header is not validated against the length of the
     * resource, producing negative lengths and negative/overrunning offsets.</p>
     * <p><b>Defect:</b> {@code Range.java:63-68} - for a first-part-only range the code does
     * {@code length = fullLength - offset} with no check that {@code offset <= fullLength}, and
     * for a second-part-only (suffix) range it does {@code offset = fullLength - rangeSecondPart}
     * with no check that {@code rangeSecondPart <= fullLength}.  Neither is clamped, and the
     * two-part case is not clamped either.</p>
     * <p><b>Correct behavior:</b> the resulting length must never be negative, the offset must
     * never be negative, and {@code offset + length} must never exceed {@code fullLength}.  (Per
     * RFC 9110 an unsatisfiable range should yield 416, but at absolute minimum the computed
     * values must be sane.)</p>
     */
    @Test
    public void test_Finding_RangeNotClampedToFileLength() {
        long fullLength = 100L;

        // case 1: first part beyond the end of the file
        Range beyondEnd = new Range(new Headers(List.of("Range: bytes=200-")), fullLength);
        assertTrue(beyondEnd.getLength() >= 0,
                "'Range: bytes=200-' against a 100-byte resource must never produce a negative length. " +
                        "It should be clamped (or rejected as unsatisfiable).  Actual length was: " + beyondEnd.getLength());
        assertTrue(beyondEnd.getOffset() + beyondEnd.getLength() <= fullLength,
                "'Range: bytes=200-' against a 100-byte resource must not read past the end of the resource. " +
                        "offset(" + beyondEnd.getOffset() + ") + length(" + beyondEnd.getLength() + ") must be <= " + fullLength);

        // case 2: enormous second part
        Range enormousSecond = new Range(new Headers(List.of("Range: bytes=0-9999999999999")), fullLength);
        assertTrue(enormousSecond.getLength() >= 0,
                "'Range: bytes=0-9999999999999' must never produce a negative length.  Actual: " + enormousSecond.getLength());
        assertTrue(enormousSecond.getOffset() + enormousSecond.getLength() <= fullLength,
                "'Range: bytes=0-9999999999999' against a 100-byte resource must be clamped to the resource length. " +
                        "offset(" + enormousSecond.getOffset() + ") + length(" + enormousSecond.getLength() + ") must be <= " + fullLength);

        // case 3: enormous suffix range
        Range enormousSuffix = new Range(new Headers(List.of("Range: bytes=-9999999999999")), fullLength);
        assertTrue(enormousSuffix.getOffset() >= 0,
                "'Range: bytes=-9999999999999' (a suffix range) against a 100-byte resource must never produce a " +
                        "negative offset.  Actual offset: " + enormousSuffix.getOffset());
        assertTrue(enormousSuffix.getLength() >= 0 && enormousSuffix.getOffset() + enormousSuffix.getLength() <= fullLength,
                "'Range: bytes=-9999999999999' against a 100-byte resource must be clamped to the resource length. " +
                        "offset(" + enormousSuffix.getOffset() + ") + length(" + enormousSuffix.getLength() + ") must be <= " + fullLength);
    }

    /*
     * ==========================================================================
     * FINDING 3 and 4: the %NULL% sentinel in query strings
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> a client can put a Java {@code null} into the query-string map.</p>
     * <p><b>Defect:</b> {@code StringUtils.java:88-94} - {@code StringUtils.decode} returns a
     * literal Java {@code null} whenever the raw value is exactly the sentinel text
     * {@code %NULL%}.  {@code RequestLine.java:219} calls that method with attacker-controlled
     * query-string values and stores the result in the query-string map.</p>
     * <p><b>Correct behavior:</b> the {@code %NULL%} sentinel is an internal encoding artifact of
     * {@code StringUtils.encode}, not something a remote client should be able to inject.  A query
     * parameter that was present on the wire must map to a non-null String (here, the literal
     * text {@code %NULL%}); handlers that call {@code .get("foo").equals(...)} must not be exposed
     * to a surprise NullPointerException.</p>
     */
    @Test
    public void test_Finding_NullSentinelInQueryString() {
        RequestLine requestLine = RequestLine.EMPTY.extractRequestLine("GET /?foo=%NULL% HTTP/1.1");
        Map<String, String> queryString = requestLine.getPathDetails().getQueryString();

        assertTrue(queryString.containsKey("foo"),
                "Sanity check: the parameter 'foo' was present in the query string, so it must be a key in the map");
        assertTrue(queryString.get("foo") != null,
                "A query-string parameter that was present on the wire must never map to a Java null. " +
                        "The value '%NULL%' is an internal sentinel of StringUtils.encode/decode and a remote client " +
                        "must not be able to inject a null into the query-string map.  Expected the literal text " +
                        "'%NULL%' (or a rejected request), but the map value was null.");
    }

    /**
     * <p><b>Finding:</b> the {@code %NULL%} sentinel lets a client bypass the duplicate-query-key
     * check, so a duplicated key silently overwrites instead of being rejected.</p>
     * <p><b>Defect:</b> {@code RequestLine.java:219-224} detects duplicates by checking whether
     * {@code Map.put} returned a non-null previous value.  Because
     * {@code StringUtils.decode("%NULL%")} ({@code StringUtils.java:88-94}) stores a real null,
     * the {@code put} for the second occurrence also returns null and the duplicate goes
     * undetected.</p>
     * <p><b>Correct behavior:</b> a duplicated query-string key must be rejected with a
     * {@link BadRequestException} regardless of the value; the check must not depend on the
     * previous value being non-null.  This test asserts both the working case and the bypass.</p>
     */
    @Test
    public void test_Finding_NullSentinelBypassesDuplicateKeyCheck() {
        // the ordinary case already works correctly today
        assertThrows(BadRequestException.class,
                () -> RequestLine.EMPTY.extractRequestLine("GET /?foo=bar&foo=baz HTTP/1.1"));

        // the same duplication, smuggled past the check with the %NULL% sentinel
        assertThrows(BadRequestException.class,
                () -> RequestLine.EMPTY.extractRequestLine("GET /?foo=%NULL%&foo=evil HTTP/1.1"));
    }

    /*
     * ==========================================================================
     * FINDING 5: empty request target crashes the parser
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> a request line with an empty request target crashes with an unchecked
     * {@link StringIndexOutOfBoundsException} rather than being reported as a bad request.</p>
     * <p><b>Defect:</b> {@code RequestLine.java:178} - {@code extractPathDetails} does
     * {@code path.charAt(0)} without first checking whether the path is empty.  The tokenizer
     * happily produces an empty path from the two-space input {@code "GET  HTTP/1.1"}.</p>
     * <p><b>Correct behavior:</b> malformed client input must produce a
     * {@link BadRequestException} (which the framework converts into a 400 response), not an
     * unchecked runtime exception from deep inside the parser.</p>
     */
    @Test
    public void test_Finding_EmptyPathThrowsBadRequest() {
        // note the two spaces - an empty request target
        assertThrows(BadRequestException.class,
                () -> RequestLine.EMPTY.extractRequestLine("GET  HTTP/1.1"));
    }

    /*
     * ==========================================================================
     * FINDING 6: streaming response advertises Content-Length: 0
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> the "unknown body length" streaming response tells the client
     * {@code Content-Length: 0} and then sends a body anyway.</p>
     * <p><b>Defect:</b> {@code Response.java:109} -
     * {@code buildStreamingResponse(StatusCode, Headers, ThrowingConsumer)} hard-codes a
     * {@code bodyLength} of {@code 0L}.  {@code WebFramework.applyContentLength}
     * ({@code WebFramework.java:196,496}) then unconditionally writes
     * {@code Content-Length: 0} onto the wire, immediately followed by the streamed body.  The
     * javadoc on the {@code Response} constructor even states that when a body length is not
     * provided the header should become {@code transfer-encoding: chunked} - that never
     * happens.</p>
     * <p><b>Correct behavior:</b> a response whose body length is unknown must not claim
     * {@code Content-Length: 0}.  It must either omit content-length and use
     * {@code Transfer-Encoding: chunked}, or send the correct length.  As written, a conformant
     * client stops reading at zero bytes and the body is either dropped or interpreted as the
     * start of the next response on the keep-alive connection.</p>
     */
    @Test
    public void test_Finding_StreamingResponseAdvertisesZeroContentLength() throws Exception {
        var wf = new WebFramework(context, default_zdt);
        var webEngine = new WebEngine(context, wf);

        final String payload = "hello world";
        wf.registerPath(GET, "stream", request -> Response.buildStreamingResponse(
                CODE_200_OK,
                Map.of("content-type", "text/plain"),
                sw -> sw.send(payload.getBytes(StandardCharsets.UTF_8))));

        List<String> contentLengthHeader;
        String bodyReceived;
        try (IServer primaryServer = webEngine.startServer()) {
            try (Socket socket = new Socket(primaryServer.getHost(), primaryServer.getPort())) {
                socket.setSoTimeout(1500);
                OutputStream os = socket.getOutputStream();
                os.write("GET /stream HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                os.flush();

                InputStream is = socket.getInputStream();
                StatusLine statusLine = extractStatusLine(inputStreamUtils.readLine(is));
                assertEquals(statusLine.rawValue(), "HTTP/1.1 200 OK");
                Headers responseHeaders = new Headers(Headers.getAllHeaders(is, inputStreamUtils));
                contentLengthHeader = responseHeaders.valueByKey("content-length");

                bodyReceived = readUntilTimeoutOrClose(is);
            }
        }
        MyThread.sleep(SERVER_CLOSE_WAIT_TIME);

        assertTrue(bodyReceived.contains(payload),
                "Sanity check: the streaming handler really did put a body on the wire.  Received: " + bodyReceived);
        assertFalse(contentLengthHeader != null && contentLengthHeader.equals(List.of("0")),
                "A streaming response built without a known body length sent " + payload.getBytes(StandardCharsets.UTF_8).length +
                        " bytes of body, but advertised 'Content-Length: 0'.  It must instead omit content-length and " +
                        "use 'Transfer-Encoding: chunked' (as the Response constructor javadoc promises), or send the " +
                        "true length.  Headers received claimed content-length=" + contentLengthHeader);
    }

    /*
     * ==========================================================================
     * FINDING 7: multipart byte accounting counts characters, not bytes
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> multipart/form-data parsing mis-counts consumed bytes whenever a
     * partition header contains non-ASCII text, and then fails to parse a perfectly valid
     * body.</p>
     * <p><b>Defect:</b> {@code BodyProcessor.java:291} does
     * {@code countBytesRead.incrementBy(s.length() + 2)} and {@code BodyProcessor.java:300} does
     * {@code allHeaders.stream().map(String::length)...} - both use {@code String.length()},
     * which counts UTF-16 chars, on lines that {@code InputStreamUtils.readLine} already decoded
     * from UTF-8.  {@code contentLength} is a byte count, so any multi-byte character in a
     * partition header (a non-ASCII filename, for instance) makes {@code countBytesRead} drift
     * below the true number of bytes consumed.</p>
     * <p><b>Correct behavior:</b> byte accounting must use the UTF-8 byte length of each line, so
     * that a body with a correct, byte-accurate Content-Length parses successfully no matter what
     * characters appear in the partition headers.</p>
     */
    @Test
    public void test_Finding_MultipartByteAccountingUsesCharsNotBytes() {
        // 100 CJK characters - 3 bytes each in UTF-8, 1 char each per String.length()
        String multiByteFilename = "漢".repeat(100);
        String body =
                "--i_am_a_boundary\r\n" +
                "Content-Disposition: form-data; name=\"myfile\"; filename=\"" + multiByteFilename + ".txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "abcdef\r\n" +
                "--i_am_a_boundary--\r\n";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        var bodyProcessor = new BodyProcessor(context);

        // Content-Length is byte-accurate, exactly as a real client would send it
        Body bodyResult = bodyProcessor.extractBodyFromInputStream(
                bodyBytes.length,
                "multipart/form-data; boundary=i_am_a_boundary",
                new ByteArrayInputStream(bodyBytes));

        assertEquals(bodyResult.getBodyType(), BodyType.MULTIPART);
        assertEqualByteArray(
                bodyResult.getPartitionByName("myfile").getFirst().getContent(),
                "abcdef".getBytes(StandardCharsets.UTF_8),
                "A multipart body with a byte-accurate Content-Length must parse correctly even when a partition " +
                        "header (here, the filename) contains multi-byte UTF-8 characters.  BodyProcessor must count " +
                        "the UTF-8 byte length of each header line, not String.length().");
    }

    /*
     * ==========================================================================
     * FINDING 8: multipart boundary not trimmed in Request
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> {@code Request.getMultipartIterable()} takes everything after
     * {@code boundary=} as the boundary value, including any parameters that follow it.</p>
     * <p><b>Defect:</b> {@code Request.java:130-135} does
     * {@code contentType.substring(indexOfBoundaryKey + boundaryKey.length())} and stops there.
     * The equivalent code in {@code BodyProcessor.determineBoundaryValue}
     * ({@code BodyProcessor.java:127-144}) correctly trims the value at the first space or
     * semicolon.  So {@code Content-Type: multipart/form-data; boundary=abc; charset=utf-8}
     * produces the boundary {@code "abc; charset=utf-8"} instead of {@code "abc"}, and parsing
     * fails.</p>
     * <p><b>Correct behavior:</b> {@code Request} must trim the boundary value at the first space
     * or semicolon, exactly as {@code BodyProcessor.determineBoundaryValue} does, and parse the
     * body normally.</p>
     */
    @Test
    public void test_Finding_MultipartBoundaryNotTrimmedInRequest() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        String body =
                "--abc\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Disposition: form-data; name=\"text1\"\r\n" +
                "\r\n" +
                "abcdef\r\n" +
                "--abc--\r\n";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bodyBytes);

        IRequest request = new Request(
                new Headers(List.of(
                        "content-length: " + bodyBytes.length,
                        "content-type: multipart/form-data; boundary=abc; charset=utf-8")),
                RequestLine.EMPTY,
                "456.456.456.456",
                socketWrapper,
                new BodyProcessor(context),
                false);

        StreamingMultipartPartition partition = request.getMultipartIterable().iterator().next();

        assertEquals(partition.getContentDisposition().getName(), "text1");
        assertTrue("abcdef".equals(new String(partition.readAllBytes(), StandardCharsets.UTF_8)),
                "Request.getMultipartIterable() must trim trailing parameters off the boundary value (using 'abc', " +
                        "not 'abc; charset=utf-8'), the same way BodyProcessor.determineBoundaryValue does.");
    }

    /*
     * ==========================================================================
     * FINDING 9: Content-Length accepts a leading plus sign
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> {@code Content-Length: +5} is accepted as the value 5.</p>
     * <p><b>Defect:</b> {@code Headers.java:113} parses the header value with
     * {@code Long.parseLong}, which accepts an optional leading {@code '+'} (and would also
     * accept a leading {@code '-'}, though the subsequent negative check catches that one).</p>
     * <p><b>Correct behavior:</b> RFC 9112 section 6.2 defines Content-Length as
     * {@code 1*DIGIT} - digits only.  A value of {@code +5} is malformed and must be rejected
     * with a {@link BadRequestException}.  Accepting it invites request-smuggling
     * disagreements with any intermediary that parses the header strictly.</p>
     */
    @Test
    public void test_Finding_ContentLengthRejectsPlusPrefix() {
        Headers headers = new Headers(List.of("Content-Length: +5"));
        assertThrows(BadRequestException.class, headers::contentLength);
    }

    /*
     * ==========================================================================
     * FINDING 10: TheBrig is never stopped on shutdown
     * ==========================================================================
     */

    /**
     * <p><b>Finding:</b> {@link FullSystem#shutdown()} never stops {@code TheBrig}, so the
     * brig's database directory stays registered in the {@link Context} forever.</p>
     * <p><b>Defect:</b> {@code FullSystem.java:227-248} - {@code shutdown()} calls
     * {@code closeCore}, which closes the servers, kills the action queues and deletes the
     * SYSTEM_RUNNING file, but never calls {@code theBrig.stop()}.  {@code TheBrig.stop()}
     * (TheBrig.java:121-127) is what calls {@code inmatesDb.stop()}, and
     * {@code DbEngine2.stop()} (DbEngine2.java:519-522) is what calls
     * {@code context.removeFromPaths(dbDirectory)} and flushes pending writes to disk.</p>
     * <p><b>Correct behavior:</b> after a full shutdown, the brig's database must have been
     * stopped: its data flushed and its path de-registered, so that a subsequent
     * {@link FullSystem} using the same context/directory can start without tripping the
     * "Attempted to register more than one database to the same path" guard in
     * {@code AbstractDb} (AbstractDb.java:100).</p>
     */
    @Test
    public void test_Finding_TheBrigIsStoppedOnShutdown() throws IOException {
        Path scratchDb = Path.of("/tmp/minum_findings_test_db_" + System.nanoTime());
        Properties properties = Constants.getConfiguredProperties();
        properties.setProperty("DB_DIRECTORY", scratchDb.toString());
        properties.setProperty("IS_THE_BRIG_ENABLED", "true");
        properties.setProperty("ENABLE_SYSTEM_RUNNING_MARKER", "false");
        properties.setProperty("SERVER_PORT", "8085");
        properties.setProperty("SSL_SERVER_PORT", "8446");

        Context myContext = buildTestingContext("FindingsWebTests - brig shutdown", properties);
        try {
            var fullSystem = new FullSystem(myContext);
            fullSystem.start();
            Path brigPath = Path.of(myContext.getConstants().dbDirectory, "the_brig");
            assertTrue(myContext.isDbPathRegistered(brigPath),
                    "Sanity check: while running, the brig's database path should be registered");

            fullSystem.shutdown();
            MyThread.sleep(50);

            assertFalse(myContext.isDbPathRegistered(brigPath),
                    "FullSystem.shutdown() must stop TheBrig, which stops its database, which de-registers its " +
                            "path (" + brigPath + ").  Because theBrig.stop() is never called, the path stays " +
                            "registered and the brig's data is never flushed - a second FullSystem on the same " +
                            "context/directory would fail with 'Attempted to register more than one database to " +
                            "the same path'.");
        } finally {
            TestFramework.shutdownTestingContext(myContext);
            MyThread.sleep(SERVER_CLOSE_WAIT_TIME);
        }
    }

    /*
     * ==========================================================================
     * helpers
     * ==========================================================================
     */

    /**
     * Read everything the server sends until the socket closes or goes quiet.
     * The socket must have had a soTimeout set.
     */
    private static String readUntilTimeoutOrClose(InputStream is) throws IOException {
        var baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (true) {
                int numRead = is.read(buffer);
                if (numRead == -1) break;
                baos.write(buffer, 0, numRead);
            }
        } catch (SocketTimeoutException ignored) {
            // this is how we know the server has said all it intends to say
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * a minimal {@link IRequest} for testing {@link WebFramework#determineIfKeepAlive}
     */
    private static IRequest makeMinimalRequest(RequestLine requestLine, Headers headers) {
        return new IRequest() {
            @Override public Headers getHeaders() {return headers;}
            @Override public RequestLine getRequestLine() {return requestLine;}
            @Override public Body getBody() {return null;}
            @Override public String getRemoteRequester() {return "";}
            @Override public ISocketWrapper getSocketWrapper() {return null;}
            @Override public Iterable<UrlEncodedKeyValue> getUrlEncodedIterable() {return null;}
            @Override public Iterable<StreamingMultipartPartition> getMultipartIterable() {return null;}
            @Override public boolean hasAccessedBody() {return false;}
            @Override public IBodyProcessor getBodyProcessor() {return null;}
            @Override public boolean isHasStartedReadingBody() {return false;}
        };
    }
}
