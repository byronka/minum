package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Context;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.renomad.minum.testing.TestFramework.*;

public class RequestTests {

    private Context context;
    private RequestLine defaultRequestLine;
    private TestLogger logger;

    @Before
    public void init() {
        this.context = buildTestingContext("Request tests");
        defaultRequestLine = new RequestLine(
                RequestLine.Method.GET,
                new PathDetails("bar", "biz", Map.of("aaa", "bbb")),
                HttpVersion.ONE_DOT_ONE,
                "",
                this.context.getLogger());
        this.logger = (TestLogger)this.context.getLogger();
    }

    @Test
    public void equalsTest() {

        EqualsVerifier.forClass(Request.class)
                .withPrefabValues(BodyProcessor.class, new BodyProcessor(context), new BodyProcessor(context))
                .withPrefabValues(Request.class,
                        new Request(
                                new Headers(List.of("a","b")),
                                new RequestLine(
                                        RequestLine.Method.GET,
                                        new PathDetails("foo", "", Map.of()),
                                        HttpVersion.ONE_DOT_ONE,
                                        "",
                                        context.getLogger()),
                                "123.123.123.123",
                                new FakeSocketWrapper(),
                                new BodyProcessor(context)
                        ),
                        new Request(
                                new Headers(List.of("c","d")),
                                new RequestLine(
                                        RequestLine.Method.GET,
                                        new PathDetails("bar", "biz", Map.of("aaa","bbb")),
                                        HttpVersion.ONE_DOT_ONE,
                                        "",
                                        context.getLogger()),
                                "456.456.456.456",
                                new FakeSocketWrapper(),
                                new BodyProcessor(context)
                        )
                )
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

    /**
     * A user may get the {@link ISocketWrapper} from the {@link Request} in
     * order to handle some complex scenarios with streaming or getting
     * large data.
     */
    @Test
    public void test_GetSocketWrapper() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        IRequest request = makeRequest(List.of("foo: bar"), socketWrapper);

        assertEquals(request.getSocketWrapper(), socketWrapper);
    }

    @Test
    public void test_Request_ToString() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        IRequest request = makeRequest(List.of("foo: bar"), socketWrapper);

        assertEquals(request.toString(), "Request{headers=Headers{headerStrings=[foo: bar]}, requestLine=RequestLine{method=GET, pathDetails=PathDetails{isolatedPath='bar', rawQueryString='biz', queryString={aaa=bbb}}, version=ONE_DOT_ONE, rawValue='', logger=TestLogger using queue: loggerPrinterRequest tests}, body=null, remoteRequester='456.456.456.456', socketWrapper=fake socket wrapper, hasStartedReadingBody=false}");
    }

    /**
     * If the content length is set above the constant maxReadSizeBytes, our system
     * will throw an exception.  Note that this is just for a safe default in cases
     * where we read the body into memory.  It is always feasible for the developer (user)
     * to either increase that constant (not recommended) or to use {@link Request#getMultipartIterable()}
     * or {@link Request#getUrlEncodedIterable()} to read when expecting very large files.
     */
    @Test
    public void test_Request_BodyTooLong() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        IRequest request = makeRequest(List.of("content-length: " + context.getConstants().maxReadSizeBytes + 1), socketWrapper);
        var ex = assertThrows(ForbiddenUseException.class, () -> request.getBody());
        assertEquals(ex.getMessage(), "It is disallowed to process a body with a length more than 10485760 bytes");
    }

    /**
     * What is a reasonable maximum for number of partitions in multipart/form? There needs
     * to be a cap on all numbers, otherwise you could get into some security problems.
     */
    @Test
    public void test_Request_Multipart_ExcessiveCountOfPartitions() throws IOException {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = makeTestMultiPartDataExcessivePartitions();
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        request.getBody();
        assertTrue(logger.doesMessageExist("Error: body had excessive number of partitions (1000).  Maximum allowed: 1000"));
    }

    /**
     * What is a reasonable maximum for number of key-value pairs in url-encoded forms? There needs
     * to be a cap on all numbers, otherwise you could get into some security problems.
     */
    @Test
    public void test_Request_UrlEncoded_ExcessiveCountOfKeyValuePairs() throws IOException {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = makeTestUrlEncodedDataExcessivePairs();
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: application/x-www-form-urlencoded"), socketWrapper);
        request.getBody();
        assertTrue(logger.doesMessageExist("Error: body had excessive number of partitions (1000).  Maximum allowed: 1000"));
    }

    /**
     * If the content type specifies multipart and a positive content length, but
     * the incoming data has no boundaries, a body of type "UNRECOGNIZED" will be returned
     */
    @Test
    public void test_Request_ImproperlyFormedMultipart() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + 10, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        Body body = request.getBody();
        assertEquals(body, new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED));
    }

    /**
     * If the content type specifies url-encoded and a positive content length, but
     * the incoming data has no boundaries, a body of type "UNRECOGNIZED" will be returned
     */
    @Test
    public void test_Request_ImproperlyFormedUrlEncoded() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = "".getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + 10, "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Body body = request.getBody();
        assertEquals(body, new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED));
    }

    /**
     * throw an exception on {@link InputStream#read()}
     */
    @Test
    public void test_Request_UrlEncoded_IOException() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Just a test");
            }
        };
        IRequest request = makeRequest(List.of("content-length: " + 10, "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Body body = request.getBody();
        assertEquals(body, new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED));
        assertTrue(logger.doesMessageExist("Unable to parse this body. returning what we have so far.  Exception message: java.io.IOException: Just a test"));
    }

    /**
     * if the url encoded data has no key, a message will be logged
     */
    @Test
    public void test_Request_ImproperlyFormedUrlEncoded_NoKey() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = "=bar".getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Body body = request.getBody();
        assertTrue(logger.doesMessageExist("Unable to parse this body. returning what we have so far.  Exception message: Unable to parse this body. no key found during parsing"));
        assertEquals(body, new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED));
    }

    /**
     * If we ask for a multipart iterable but the data is empty.
     */
    @Test
    public void test_Request_getMultipartIterable_EdgeCase_Empty() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        // set an empty body
        socketWrapper.is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        IRequest request = makeRequest(List.of("content-length: 0", "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        Iterable<StreamingMultipartPartition> multipartIterable = request.getMultipartIterable();
        Iterator<StreamingMultipartPartition> iterator = multipartIterable.iterator();
        assertThrows(NoSuchElementException.class, () -> iterator.next());
    }


    /**
     * If we ask for a multipart iterable but boundary in the header is invalid
     */
    @Test
    public void test_Request_getMultipartIterable_EdgeCase_No_Valid_Boundary() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                abcdef\r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text2"\r
                \r
                ghi\r
                --i_am_a_boundary--\r
                """.stripLeading().getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; "), socketWrapper);
        var ex = assertThrows(WebServerException.class, () -> request.getMultipartIterable());
        assertEquals(ex.getMessage(), "Did not find a valid boundary value for the multipart input. Returning an empty map and the raw bytes for the body. Header was: content-type: multipart/form-data; ");
    }


    /**
     * If we ask for a multipart iterable but boundary in the header is invalid
     */
    @Test
    public void test_Request_getMultipartIterable_EdgeCase_No_Valid_Boundary_2() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                abcdef\r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text2"\r
                \r
                ghi\r
                --i_am_a_boundary--\r
                """.stripLeading().getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; boundary="), socketWrapper);
        var ex = assertThrows(WebServerException.class, () -> request.getMultipartIterable());
        assertEquals(ex.getMessage(), "Boundary value was blank. Returning an empty map and the raw bytes for the body. Header was: content-type: multipart/form-data; boundary=");
    }

    /**
     * If we ask for a multipart iterable but boundary in the header is invalid
     */
    @Test
    public void test_Request_getMultipartIterable_EdgeCase_No_Valid_Boundary_3() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                abcdef\r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text2"\r
                \r
                ghi\r
                --i_am_a_boundary--\r
                """.stripLeading().getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; boundary=foo_foo"), socketWrapper);
        Iterable<StreamingMultipartPartition> multipartIterable = request.getMultipartIterable();
        Iterator<StreamingMultipartPartition> iterator = multipartIterable.iterator();
        var ex = assertThrows(WebServerException.class, () -> iterator.next());
        assertEquals(ex.getMessage(), "java.io.IOException: Error: First line must contain the expected boundary value. Expected to find: foo_foo in: --i_am_a_boundary");
    }

    /**
     * If the content length is longer than what we were given, an
     * exception will get thrown in read()
     */
    @Test
    public void test_Request_Multipart_IOException_ReadingBody() {
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                I am a value that is text\r
                """.getBytes(StandardCharsets.UTF_8);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + (bytes.length + 10), "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        request.getBody();
        assertTrue(logger.doesMessageExist("Unable to parse this body. returning what we have so far.  Exception message: java.io.IOException: Error: The inputstream has closed unexpectedly while reading"));
    }

    @Test
    public void test_Request_Multipart_ImproperlyFormed() {
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                I am a valu\r
                """.getBytes(StandardCharsets.UTF_8);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = new ByteArrayInputStream(bytes);
        int extraBytes = 1;
        IRequest request = makeRequest(List.of("content-length: " + (bytes.length + extraBytes), "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);

        request.getBody();

        assertTrue(logger.doesMessageExist("Unable to parse this body. returning what we have so far.  Exception message: java.io.IOException: Error: The inputstream has closed unexpectedly while reading"));
    }

    @Test
    public void test_Request_Multipart_ImproperlyFormed_CorrectContentLength() {
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                a\r
                --i_am_a_boundary\r
                """.stripIndent().getBytes(StandardCharsets.UTF_8);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = new ByteArrayInputStream(bytes);
        int extraBytes = 0;
        IRequest request = makeRequest(List.of("content-length: " + (bytes.length + extraBytes), "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);

        request.getBody();
    }

    /**
     * If we ask for a url-encoded iterable but the data is empty.
     */
    @Test
    public void test_Request_getUrlEncoded_EdgeCase_Empty() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        // set an empty body
        socketWrapper.is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        IRequest request = makeRequest(List.of("content-length: 0", "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Iterable<UrlEncodedKeyValue> urlEncodedIterable = request.getUrlEncodedIterable();

        assertThrows(NoSuchElementException.class, () -> urlEncodedIterable.iterator().next());
    }

    /**
     * Content length too long for url-encoded data.  An exception should be thrown.
     */
    @Test
    public void test_Request_getUrlEncoded_EdgeCase_ContentLengthTooLong() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = new ByteArrayInputStream("foo=bar".getBytes(StandardCharsets.UTF_8));
        IRequest request = makeRequest(List.of("content-length: 20", "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Iterable<UrlEncodedKeyValue> urlEncodedIterable = request.getUrlEncodedIterable();
        UrlEncodedKeyValue next = urlEncodedIterable.iterator().next();

        var ex = assertThrows(IOException.class, () -> next.getUedg().readAllBytes());

        assertEquals(ex.getMessage(), "Error: The inputstream has closed unexpectedly while reading");
    }

    /**
     * Content length too short for url-encoded data.  Closing the UrlEncodedDataGetter should
     * help finish reading all the data.
     */
    @Test
    public void test_Request_getUrlEncoded_EdgeCase_ContentLengthNotLongEnough() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = new ByteArrayInputStream("foo=bar".getBytes(StandardCharsets.UTF_8));
        IRequest request = makeRequest(List.of("content-length: 5", "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Iterable<UrlEncodedKeyValue> urlEncodedIterable = request.getUrlEncodedIterable();
        UrlEncodedKeyValue next = urlEncodedIterable.iterator().next();
        byte[] result;
        try (UrlEncodedDataGetter uedg = next.getUedg()) {
            result = uedg.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertEquals(1, result.length);
        assertEquals((byte)'b', result[0]);
    }

    /**
     * Running {@link UrlEncodedDataGetter#close()} should loop through all the remaining data,
     * so that the invocation of next on {@link Request#getUrlEncodedIterable()} will have the
     * input stream at the right place.
     */
    @Test
    public void test_Request_getUrlEncoded_EdgeCase_PlayingWithClose() throws IOException {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = "foo=bar&biz=baz".getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: application/x-www-form-urlencoded"), socketWrapper);
        Iterable<UrlEncodedKeyValue> urlEncodedIterable = request.getUrlEncodedIterable();
        Iterator<UrlEncodedKeyValue> iterator = urlEncodedIterable.iterator();
        UrlEncodedKeyValue part1 = iterator.next();
        UrlEncodedDataGetter uedg1 = part1.getUedg();

        // this should read from the inputstream until the end of the value, leaving us
        // at the start of the next key.
        uedg1.close();

        UrlEncodedKeyValue part2 = iterator.next();
        UrlEncodedDataGetter uedg2 = part2.getUedg();
        assertEquals(new String(uedg2.readAllBytes(), StandardCharsets.UTF_8), "baz");
    }

    /**
     * If the user runs {@link Request#getBody()} and then tries to get the iterable, it should
     * throw an exception.
     */
    @Test
    public void test_Request_getUrlEncoded_EdgeCase_ComplaintAfterGetBody() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = "foo=bar&biz=baz".getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length), socketWrapper);
        request.getBody();
        var ex = assertThrows(WebServerException.class, () -> request.getUrlEncodedIterable());
        assertEquals(ex.getMessage(), "Requesting this after getting the body with getBody() will result in incorrect behavior.  If you intend to work with the Request at this level, do not use getBody");
    }

    /**
     * Running {@link StreamingMultipartPartition#close()} should loop through all the remaining data,
     * so that the invocation of next on {@link Request#getMultipartIterable()} will have the
     * input stream at the right place.
     */
    @Test
    public void test_Request_getMultipartForm_EdgeCase_PlayingWithClose() throws IOException {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                abcdef\r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text2"\r
                \r
                ghi\r
                --i_am_a_boundary--\r
                """.stripLeading().getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        Iterable<StreamingMultipartPartition> multipartIterable = request.getMultipartIterable();
        Iterator<StreamingMultipartPartition> iterator = multipartIterable.iterator();
        StreamingMultipartPartition part1 = iterator.next();
        byte firstChar = (byte)part1.read();
        assertEquals((byte)'a', firstChar);

        // this should read from the inputstream until the end of the value, leaving us
        // at the start of the next key.
        part1.close();

        StreamingMultipartPartition part2 = iterator.next();
        byte[] part2Bytes = part2.readAllBytes();
        assertEquals(new String(part2Bytes, StandardCharsets.UTF_8), "ghi");
        assertEquals(part2.getContentDisposition().toString(), "ContentDisposition{name='text2', filename=''}");
    }

    @Test
    public void test_Request_getMultipartForm_EdgeCase_ComplaintAfterGetBody() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                abcdef\r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text2"\r
                \r
                ghi\r
                --i_am_a_boundary--\r
                """.stripLeading().getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        request.getBody();
        var ex = assertThrows(WebServerException.class, () -> request.getMultipartIterable());
        assertEquals(ex.getMessage(), "Requesting this after getting the body with getBody() will result in incorrect behavior.  If you intend to work with the Request at this level, do not use getBody");
    }

    /**
     * What if the user tries to parse their data as multipart but it's actually url-encoded?
     */
    @Test
    public void test_Request_getMultipartForm_EdgeCase_UrlEncodedData() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = """
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text1"\r
                \r
                abcdef\r
                --i_am_a_boundary\r
                Content-Type: text/plain\r
                Content-Disposition: form-data; name="text2"\r
                \r
                ghi\r
                --i_am_a_boundary--\r
                """.stripLeading().getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);
        var ex = assertThrows(WebServerException.class, () -> request.getUrlEncodedIterable());
        assertEquals(ex.getMessage(), "This request was not sent with a content type of application/x-www-form-urlencoded.  The content type was: content-type: multipart/form-data; boundary=i_am_a_boundary");
    }

    /**
     * What if the user tries to parse their data as url-encoded but it's actually multipart?
     */
    @Test
    public void test_Request_getUrlEncoded_EdgeCase_MultipartData() {
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        byte[] bytes = "foo=bar&biz=baz".getBytes(StandardCharsets.UTF_8);
        socketWrapper.is = new ByteArrayInputStream(bytes);
        IRequest request = makeRequest(List.of("content-length: " + bytes.length, "content-type: application/x-www-form-urlencoded"), socketWrapper);
        var ex = assertThrows(WebServerException.class, () -> request.getMultipartIterable());
        assertEquals(ex.getMessage(), "This request was not sent with a content type of multipart/form-data.  The content type was: content-type: application/x-www-form-urlencoded");
    }


    /**
     * Parsing through the url-encoded data should be straightforward.  we just
     * zip along, changing our status based on encountering ampersands and equal
     * signs.  we just need a way to somehow provide an inputstream or something like
     * an input stream that, we can pull a byte at a time, and it in turn pulls
     * a byte at a time from the socket.
     * <br>
     * UrlEncodedDataGetter.ready will return the next byte, or readAllBytes, predicated on whether the
     * byte is *not* an ampersand.
     */
    @Test
    public void testReadingAStreamingUrlEncoded() throws IOException {
        byte[] dataBytes = "abc=123&foo=bar".getBytes(StandardCharsets.UTF_8);
        var inputStream = new ByteArrayInputStream(dataBytes);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = inputStream;
        byte result;
        byte[] results;
        List<String> keys = List.of("abc", "foo");
        List<Character> firstByte = List.of('1', 'b');
        List<byte[]> remainders = List.of(
                "23".getBytes(StandardCharsets.UTF_8),
                "ar".getBytes(StandardCharsets.UTF_8));
        int index = 0;
        IRequest request = makeRequest(List.of("content-length: " + dataBytes.length, "content-type: application/x-www-form-urlencoded"), socketWrapper);

        Iterable<UrlEncodedKeyValue> pairs = request.getUrlEncodedIterable();

        for (UrlEncodedKeyValue pair : pairs) {
            String key = pair.getKey();
            assertEquals(key, keys.get(index));
            try (UrlEncodedDataGetter uedg = pair.getUedg()) {
                result = (byte) uedg.read();
                assertEquals((char) result, firstByte.get(index));
                results = uedg.readAllBytes();
            }
            assertEquals(new String(results, StandardCharsets.UTF_8), new String(remainders.get(index), StandardCharsets.UTF_8));
            index++;
        }
    }



    /**
     * Similar to {@link #testReadingAStreamingUrlEncoded}, this will read the multipart form data
     * as a stream.  The boundary is the value provided us in the header.  So
     * all we need to do is to determine when we are at a boundary by the
     * simplest way possible - create an array the size of the value we are
     * seeking and moving it as a window along the data, halting when we
     * have a match.
     * <br>
     * MultiPartFormDataGetter.read will return the next byte, or readAllBytes, predicated
     * on whether we have not yet hit the window.  The window will scan ahead, so that
     * the value returned to us is behind what we have read off the socket.
     */
    @Test
    public void testReadingStreamingMultipart() throws IOException {
        byte[] multipartDataBytes = makeTestMultiPartData();
        var inputStream = new ByteArrayInputStream(multipartDataBytes);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = inputStream;
        int index = 0;
        var types = List.of("text/plain", "application/octet-stream");
        var names = List.of("text1", "image_uploads");
        var filenames = List.of("", "photo_preview.jpg");
        var values = List.of("I am a value that is text".getBytes(StandardCharsets.UTF_8), new byte[]{1,2,3});
        IRequest request = makeRequest(List.of("content-length: " + multipartDataBytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);

        Iterable<StreamingMultipartPartition> partitions = request.getMultipartIterable();

        for (StreamingMultipartPartition partition : partitions) {
            try (partition) {
                Headers headers1 = partition.getHeaders();
                String contentTypeHeader = headers1.valueByKey("content-type").getFirst();
                assertEquals(contentTypeHeader, types.get(index));
                String name = partition.getContentDisposition().getName();
                assertEquals(name, names.get(index));
                String filename = partition.getContentDisposition().getFilename();
                assertEquals(filename, filenames.get(index));
                byte[] bytes = partition.readAllBytes();
                assertEqualByteArray(bytes, values.get(index));
                index++;
            }
        }
    }

    @Test
    public void testReadingEmptyStreamingMultipart() throws IOException {
        byte[] multipartDataBytes = makeTestMultiPartDataEmpty();
        var inputStream = new ByteArrayInputStream(multipartDataBytes);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = inputStream;
        var names = List.of("image_uploads", "short_description", "long_description");
        IRequest request = makeRequest(List.of("content-length: " + multipartDataBytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);

        Iterable<StreamingMultipartPartition> partitions = request.getMultipartIterable();

        int index = 0;
        for (StreamingMultipartPartition partition : partitions) {
            Headers headers1 = partition.getHeaders();
            if (index == 0) {
                String contentTypeHeader = headers1.valueByKey("content-type").getFirst();
                assertEquals(contentTypeHeader, "application/octet-stream");
            }
            String name = partition.getContentDisposition().getName();
            assertEquals(name, names.get(index));
            byte[] bytes = partition.readAllBytes();
            assertEqualByteArray(bytes, new byte[0]);
            index += 1;
        }
    }

    /**
     * This is similar to {@link #testReadingStreamingMultipart()} but it uses
     * the {@link java.io.InputStream#read(byte[])} method to obtain the bytes.
     */
    @Test
    public void testReadingStreamingMultipart_AlternateCase_UsingBuffer() throws IOException {
        byte[] multipartDataBytes = makeTestMultiPartData_InputWithMultipleOption();
        Path kittyPath = Path.of("src/test/resources/kitty.jpg");
        byte[] kittyBytes = Files.readAllBytes(kittyPath);
        var inputStream = new ByteArrayInputStream(multipartDataBytes);
        FakeSocketWrapper socketWrapper = new FakeSocketWrapper();
        socketWrapper.is = inputStream;
        int index = 0;
        var types = List.of("text/plain", "application/octet-stream", "application/octet-stream", "application/octet-stream");
        var names = List.of("text1", "image_uploads", "kitty", "kitty");
        var values = List.of("I am a value that is text".getBytes(StandardCharsets.UTF_8), new byte[]{1,2,3}, kittyBytes, kittyBytes);
        IRequest request = makeRequest(List.of("content-length: " + multipartDataBytes.length, "content-type: multipart/form-data; boundary=i_am_a_boundary"), socketWrapper);

        Iterable<StreamingMultipartPartition> partitions = request.getMultipartIterable();

        for (StreamingMultipartPartition partition : partitions) {
            Headers headers1 = partition.getHeaders();
            String contentTypeHeader = headers1.valueByKey("content-type").getFirst();
            assertEquals(contentTypeHeader, types.get(index));
            String name = partition.getContentDisposition().getName();
            assertEquals(name, names.get(index));
            var baos = new ByteArrayOutputStream();
            copy(partition, baos);
            assertEqualByteArray(baos.toByteArray(), values.get(index));
            index++;
        }
    }

    @Test
    public void testSimplerRequest() {
        var r = buildSimpleRequest(List.of());
        assertEquals(r.getRemoteRequester(), "123.132.123.123");
        assertEquals(r.getBody(), Body.EMPTY);
        assertEquals(r.getRequestLine(), RequestLine.EMPTY);
        assertEquals(r.getHeaders(), new Headers(List.of()));
        assertEquals(r.toString(), "Request{headers=Headers{headerStrings=[]}, requestLine=RequestLine{method=NONE, pathDetails=PathDetails{isolatedPath='', rawQueryString='', queryString={}}, version=NONE, rawValue='', logger=null}, body=Body{bodyMap={}, bodyType=NONE}, remoteRequester='123.132.123.123', socketWrapper=fake socket wrapper, hasStartedReadingBody=false}");
    }

    @Test
    public void testSimplerRequest2() {
        var r = buildSimpleRequest(List.of("Content-Type: application/x-www-form-urlencoded"));
        assertTrue(r.getUrlEncodedIterable() == null);
        assertEquals(r.toString(), "Request{headers=Headers{headerStrings=[Content-Type: application/x-www-form-urlencoded]}, requestLine=RequestLine{method=NONE, pathDetails=PathDetails{isolatedPath='', rawQueryString='', queryString={}}, version=NONE, rawValue='', logger=null}, body=null, remoteRequester='123.132.123.123', socketWrapper=fake socket wrapper, hasStartedReadingBody=true}");
    }

    @Test
    public void testSimplerRequest3() {
        var r = buildSimpleRequest(List.of("Content-Type: multipart/form-data; boundary=i_am_a_boundary"));
        assertTrue(r.getMultipartIterable() == null);
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_1() {
        var r = buildSimpleRequest(List.of());
        r.getBody();
        var ex = assertThrows(WebServerException.class, r::getSocketWrapper);
        assertEquals(ex.getMessage(), "Requesting this after getting the body with getBody() will result in incorrect behavior.  If you intend to work with the Request at this level, do not use getBody");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_2() {
        var r = buildSimpleRequest(List.of());
        r.getSocketWrapper();
        var ex = assertThrows(WebServerException.class, r::getBody);
        assertEquals(ex.getMessage(), "The InputStream in Request has already been accessed for reading, preventing body extraction from stream. If intending to use getBody(), use it exclusively");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_3() {
        var r = buildSimpleRequest(List.of("Content-Type: multipart/form-data; boundary=i_am_a_boundary"));
        r.getMultipartIterable();
        var ex = assertThrows(WebServerException.class, r::getBody);
        assertEquals(ex.getMessage(), "The InputStream in Request has already been accessed for reading, preventing body extraction from stream. If intending to use getBody(), use it exclusively");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_4() {
        var r = buildSimpleRequest(List.of("Content-Type: application/x-www-form-urlencoded"));
        r.getUrlEncodedIterable();
        var ex = assertThrows(WebServerException.class, r::getBody);
        assertEquals(ex.getMessage(), "The InputStream in Request has already been accessed for reading, preventing body extraction from stream. If intending to use getBody(), use it exclusively");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_5() {
        var r = buildSimpleRequest(List.of());
        r.getBody();
        var ex = assertThrows(WebServerException.class, r::getUrlEncodedIterable);
        assertEquals(ex.getMessage(), "Requesting this after getting the body with getBody() will result in incorrect behavior.  If you intend to work with the Request at this level, do not use getBody");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_6() {
        var r = buildSimpleRequest(List.of());
        r.getSocketWrapper();
        var ex = assertThrows(WebServerException.class, r::getMultipartIterable);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_7() {
        var r = buildSimpleRequest(List.of());
        r.getSocketWrapper();
        var ex = assertThrows(WebServerException.class, r::getUrlEncodedIterable);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_8() {
        var r = buildSimpleRequest(List.of());
        r.getBody();
        var ex = assertThrows(WebServerException.class, r::getMultipartIterable);
        assertEquals(ex.getMessage(), "Requesting this after getting the body with getBody() will result in incorrect behavior.  If you intend to work with the Request at this level, do not use getBody");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_9() {
        var r = buildSimpleRequest(List.of());
        r.getSocketWrapper();
        var ex = assertThrows(WebServerException.class, r::getSocketWrapper);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_10() {
        var r = buildSimpleRequest(List.of("Content-Type: application/x-www-form-urlencoded"));
        r.getUrlEncodedIterable();
        var ex = assertThrows(WebServerException.class, r::getUrlEncodedIterable);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_11() {
        var r = buildSimpleRequest(List.of("Content-Type: multipart/form-data; boundary=i_am_a_boundary"));
        r.getMultipartIterable();
        var ex = assertThrows(WebServerException.class, r::getMultipartIterable);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_12() {
        var r = buildSimpleRequest(List.of("Content-Type: multipart/form-data; boundary=i_am_a_boundary"));
        r.getMultipartIterable();
        var ex = assertThrows(WebServerException.class, r::getSocketWrapper);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }

    @Test
    public void testRequest_ExpectComplaintAfterBegunReading_13() {
        var r = buildSimpleRequest(List.of("Content-Type: application/x-www-form-urlencoded"));
        r.getUrlEncodedIterable();
        var ex = assertThrows(WebServerException.class, r::getSocketWrapper);
        assertEquals(ex.getMessage(), "The InputStream has begun processing elsewhere.  Results are invalid.");
    }


    private IRequest buildSimpleRequest(List<String> headerStrings) {
        var headers = new Headers(headerStrings);
        var requestLine = RequestLine.EMPTY;
        String remoteRequester = "123.132.123.123";
        var socketWrapper = new FakeSocketWrapper();
        var bodyProcessor = new FakeBodyProcessor();
        bodyProcessor.data = Body.EMPTY;

        return new Request(headers, requestLine, remoteRequester, socketWrapper, bodyProcessor);
    }

    void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

    private IRequest makeRequest(List<String> headers, ISocketWrapper socketWrapper) {
        return new Request(
                new Headers(headers),
                defaultRequestLine,
                "456.456.456.456",
                socketWrapper,
                new BodyProcessor(context)
        );
    }

    private static byte[] makeTestMultiPartData() throws IOException {
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
    }

    /**
     * What if all the partitions are empty?
     */
    private static byte[] makeTestMultiPartDataEmpty() throws IOException {
        final var baos = new ByteArrayOutputStream();
        baos.write(
                """
                --i_am_a_boundary\r
                Content-Disposition: form-data; name="image_uploads"; filename=""
                Content-Type: application/octet-stream
                \r
                \r
                --i_am_a_boundary\r
                Content-Disposition: form-data; name="short_description"
                \r
                \r
                --i_am_a_boundary\r
                Content-Disposition: form-data; name="long_description"
                \r
                \r
                --i_am_a_boundary\r--
                """.getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }

    /**
     * What if there are too many partitions?
     */
    private static byte[] makeTestMultiPartDataExcessivePartitions() throws IOException {
        final var baos = new ByteArrayOutputStream();
        String value = """
                --i_am_a_boundary\r
                Content-Disposition: form-data; name="foo"
                \r
                aaa\r
                """.repeat(1001)
                + "--i_am_a_boundary--";
        baos.write(value.getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    /**
     * What if there are too many key-value pairs?
     */
    private static byte[] makeTestUrlEncodedDataExcessivePairs() throws IOException {
        final var baos = new ByteArrayOutputStream();
        var sb = new StringBuilder();
        for (int i = 0; i <= 1000; i++) {
            sb.append(String.format("%d=%d&", i, 1));
        }
        sb.append("1001=1");
        baos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    /**
     * This creates a multipart request resembling what is generated when the user
     * chooses the "multiple" option on a file input element.
     */
    private static byte[] makeTestMultiPartData_InputWithMultipleOption() throws IOException {
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
    }


    public static String boxedByteArrayToString(Byte[] boxedByteArray) {
        var baos = new ByteArrayOutputStream();
        for (int i = 0; i < boxedByteArray.length; i++) {
            Byte b = boxedByteArray[i];
            baos.write(b == null ? 0 : b);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

}
