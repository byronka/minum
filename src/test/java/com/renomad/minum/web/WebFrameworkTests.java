package com.renomad.minum.web;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.Inmate;
import com.renomad.minum.security.UnderInvestigation;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileReader;
import com.renomad.minum.utils.IFileReader;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.utils.ThrowingRunnable;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.StatusLine.StatusCode.*;

public class WebFrameworkTests {

    private WebFramework webFramework;
    static final ZonedDateTime default_zdt = ZonedDateTime.of(2022, Month.JANUARY.getValue(), 4, 9, 25, 0, 0, ZoneId.of("UTC"));
    private Context context;
    private TestLogger logger;
    /**
     * Just a boring empty Headers instance for some of the methods that
     * need it but where we aren't doing anything with it.
     */
    private final Headers defaultHeaders = new Headers(List.of());

    @Before
    public void initialize() {
        context = buildTestingContext("webframework_tests");
        webFramework = new WebFramework(context, default_zdt);
        logger = (TestLogger)context.getLogger();
    }


    @Test
    public void test_readStaticFile_CSS() {
        IResponse response = webFramework.readStaticFile("main.css", defaultHeaders);

        assertEquals(response.getStatusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertTrue(response.getBody().length > 0);
        assertEquals(response.getExtraHeaders().get("content-type"), "text/css");
    }

    @Test
    public void test_readStaticFile_JS() {
        IResponse response = webFramework.readStaticFile("index.js", defaultHeaders);

        assertEquals(response.getStatusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertTrue(response.getBody().length > 0);
        assertEquals(response.getExtraHeaders().get("content-type"), "application/javascript");
    }

    @Test
    public void test_readStaticFile_HTML() {
        IResponse response = webFramework.readStaticFile("index.html", defaultHeaders);

        assertEquals(response.getStatusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertTrue(response.getBody().length > 0);
        assertEquals(response.getExtraHeaders().get("content-type"), "text/html");
    }

    /**
     * If a user requests a file with .. in front, that means go up
     * a directory - we don't really want that happening.
     */
    @Test
    public void test_readStaticFile_Edge_OutsideDirectory() {
        IResponse response = webFramework.readStaticFile("../templates/auth/login_page_template.html", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * Edge case - forward slashes
     */
    @Test
    public void test_ReadFile_Edge_ForwardSlashes() {
        IResponse response = webFramework.readStaticFile("//index.html", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * Edge case - colon
     */
    @Test
    public void test_readStaticFile_Edge_Colon() {
        IResponse response = webFramework.readStaticFile(":", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * Edge case - a directory
     */
    @Test
    public void test_readStaticFile_Edge_Directory() {
        IResponse response = webFramework.readStaticFile("src/test/resources/", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_readStaticFile_Edge_CurrentDirectory() {
        IResponse response = webFramework.readStaticFile("./", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_readStaticFile_EdgeCase() {
        IResponse response = webFramework.readStaticFile("./", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_readStaticFile_IOException() {
        webFramework = new WebFramework(context, default_zdt, throwingFileReader);
        IResponse response = webFramework.readStaticFile("Foo", defaultHeaders);

        assertEquals(response.getStatusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * If we encounter a file we don't recognize, we'll label it as application/octet-stream.  Browsers
     * won't know what to do with this, so they will treat it as if the Content-Disposition header was set
     * to attachment, and propose a "Save As" dialog.  This will make it clearer when data has not
     * been labeled with a proper mime.
     */
    @Test
    public void test_Edge_ApplicationOctetStream() {
        var response = webFramework.readStaticFile("Foo", defaultHeaders);
        assertEquals(response.getExtraHeaders().get("content-type"), "application/octet-stream");
    }

    /**
     * Users can add more mime types to our system by registering them
     * in the minum.config file in EXTRA_MIME_MAPPINGS.
     */
    @Test
    public void test_ExtraMimeMappings() {
        var input = List.of("png","image/png","wav","audio/wav");
        webFramework.readExtraMimeMappings(input);
        var mappings = webFramework.getSuffixToMimeMappings();
        assertEquals(mappings.get("png"), "image/png");
        assertEquals(mappings.get("wav"), "audio/wav");
    }

    /**
     * while reading the extra mappings, bad syntax will cause a clear failure
     */
    @Test
    public void test_ExtraMimeMappings_BadSyntax() {
        var input = List.of("png","image/png","EXTRA_WORD_HERE","wav","audio/wav");
        var ex = assertThrows(InvariantException.class, () -> webFramework.readExtraMimeMappings(input));
        assertEquals(ex.getMessage(), "input must be even (key + value = 2 items). Your input: [png, image/png, EXTRA_WORD_HERE, wav, audio/wav]");
    }

    /**
     * If there's no values, it should work fine, it should simply not add any new mime mappings
     */
    @Test
    public void test_ExtraMimeMappings_NoValues() {
        var mappings = webFramework.getSuffixToMimeMappings();
        int before = mappings.size();
        List<String> input = List.of();

        webFramework.readExtraMimeMappings(input);

        int after = mappings.size();
        assertEquals(before,after);
    }

    @Test
    public void test_ExtraMimeMappings_Null() {
        var mappings = webFramework.getSuffixToMimeMappings();
        int before = mappings.size();

        webFramework.readExtraMimeMappings(null);

        int after = mappings.size();
        assertEquals(before,after);
    }

    /**
     * A {@link FileReader} that always throws an IOException
     */
    IFileReader throwingFileReader = path -> {
        throw new IOException("Testing");
    };


    @Test
    public void testReadTimedOut() {
        var fakeSocketWrapper = new FakeSocketWrapper();
        WebFramework.handleReadTimedOut(fakeSocketWrapper, new IOException("Read timed out"), logger);
        assertTrue(logger.doesMessageExist("Read timed out - remote address"));
    }

    /**
     * two booleans here:
     * suspicious clues is empty, and whether the brig is null
     * true true
     * true false
     * false true
     * false false
     *
     * or...
     *
     * suspicousClues   brig
     * --------------   ----
     * empty             null
     * notEmpty          null
     * empty             nonNull
     * notEmpty          nonNull
     *
     * Only if the brig is non-null, and underInvestigation finds
     * something suspicious, will it add a value to the brig
     */
    @Test
    public void testHandleIoException() {
        var fakeSocketWrapper1 = new FakeSocketWrapper();
        fakeSocketWrapper1.getRemoteAddrAction = () -> "11.11.11.11";
        var fakeSocketWrapper2 = new FakeSocketWrapper();
        fakeSocketWrapper2.getRemoteAddrAction = () -> "22.22.22.22";
        var fakeSocketWrapper3 = new FakeSocketWrapper();
        fakeSocketWrapper3.getRemoteAddrAction = () -> "33.33.33.33";
        var fakeSocketWrapper4 = new FakeSocketWrapper();
        fakeSocketWrapper4.getRemoteAddrAction = () -> "44.44.44.44";

        var underInvestigation = new UnderInvestigation(context.getConstants());

        // empty, null - nothing added to brig
        WebFramework.handleIOException(fakeSocketWrapper1, new IOException(""), logger, null, underInvestigation, 10);
        assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("is looking for vulnerabilities, for this", 1));
        // notEmpty, null - nothing added to brig
        WebFramework.handleIOException(fakeSocketWrapper2, new IOException("The client supported protocol versions"), logger, null, underInvestigation, 10);
        assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("is looking for vulnerabilities, for this", 1));
        // empty, nonNull - nothing added to brig
        WebFramework.handleIOException(fakeSocketWrapper3, new IOException(""), logger, theBrigMock, underInvestigation, 10);
        assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("is looking for vulnerabilities, for this", 1));
        // notEmpty, nonNull - added, and logged
        WebFramework.handleIOException(fakeSocketWrapper4, new IOException("The client supported protocol versions"), logger, theBrigMock, underInvestigation, 10);
        assertTrue(logger.doesMessageExist("is looking for vulnerabilities, for this"));
        assertTrue(theBrigMock.isInJail("44.44.44.44_vuln_seeking"));
    }

    @Test
    public void test_HandleForbiddenUse() {
        WebFramework.handleForbiddenUse(
                new FakeSocketWrapper(),
                new ForbiddenUseException("testing forbiddenUse"),
                logger,
                null,
                1);
        assertTrue(logger.doesMessageExist("theBrig is null at handleForbiddenUse, will not store address in database"));
    }

    ITheBrig theBrigMock = new ITheBrig() {

        final Map<String, Long> jail = new HashMap<>();

        @Override
        public ITheBrig initialize() {
            return null;
        }

        @Override
        public void stop() {

        }

        @Override
        public boolean sendToJail(String clientIdentifier, long sentenceDuration) {
            jail.put(clientIdentifier, sentenceDuration);
            return true;
        }

        @Override
        public boolean isInJail(String clientIdentifier) {
            return jail.containsKey(clientIdentifier);
        }

        @Override
        public List<Inmate> getInmates() {
            return null;
        }
    };

    @Test
    public void test_makePrimaryHttpHandler_throwingIOException() throws Exception {
        FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
        fakeSocketWrapper.is = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Testing IOExceptions");
            }
        };
        ThrowingRunnable throwingRunnable = webFramework.makePrimaryHttpHandler(fakeSocketWrapper, theBrigMock);
        throwingRunnable.run();
    }

    @Test
    public void test_compressIfRequested() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        Response incomingResponse = (Response)Response.buildResponse(CODE_200_OK, Map.of("content-type", "text/plain"), "a".repeat(1000));
        IResponse compressedResponse = WebFramework.compressBodyIfRequested(incomingResponse, List.of("accept-encoding: gzip"), stringBuilder, 999);
        assertTrue(incomingResponse.getBody().length > compressedResponse.getBody().length);

    }


}
