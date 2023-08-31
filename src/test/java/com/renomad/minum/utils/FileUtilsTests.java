package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.StatusLine.StatusCode._400_BAD_REQUEST;
import static com.renomad.minum.web.StatusLine.StatusCode._404_NOT_FOUND;

public class FileUtilsTests {
    private static FileUtils fileUtils;

    @BeforeClass
    public static void init() {
        var context = buildTestingContext("unit_tests");
        var logger = (TestLogger) context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @Test
    public void test_FileUtils_CSS() {
        Response response = fileUtils.readStaticFile("main.css");

        assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        assertTrue(response.body().length > 0);
        assertEquals(response.extraHeaders().get("content-type"), "text/css");
    }

    @Test
    public void test_FileUtils_JS() {
        Response response = fileUtils.readStaticFile("index.js");

        assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        assertTrue(response.body().length > 0);
        assertEquals(response.extraHeaders().get("content-type"), "application/javascript");
    }

    @Test
    public void test_FileUtils_HTML() {
        Response response = fileUtils.readStaticFile("index.html");

        assertEquals(response.statusCode(), StatusLine.StatusCode._200_OK);
        assertTrue(response.body().length > 0);
        assertEquals(response.extraHeaders().get("content-type"), "text/html");
    }

    /**
     * If a user requests a file with .. in front, that means go up
     * a directory - we don't really want that happening.
     */
    @Test
    public void test_FileUtils_Edge_OutsideDirectory() {
        Response response = fileUtils.readStaticFile("../templates/auth/login_page_template.html");

        assertEquals(response.statusCode(), _400_BAD_REQUEST);
    }

    /**
     * Edge case - forward slashes
     */
    @Test
    public void test_FileUtils_Edge_ForwardSlashes() {
        Response response = fileUtils.readStaticFile("//index.html");

        assertEquals(response.statusCode(), _400_BAD_REQUEST);
    }

    /**
     * Edge case - colon
     */
    @Test
    public void test_FileUtils_Edge_Colon() {
        Response response = fileUtils.readStaticFile(":");

        assertEquals(response.statusCode(), _400_BAD_REQUEST);
    }

    /**
     * Edge case - a directory
     */
    @Test
    public void test_FileUtils_Edge_Directory() {
        Response response = fileUtils.readStaticFile("src/test/resources/");

        assertEquals(response.statusCode(), _404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_FileUtils_Edge_CurrentDirectory() {
        Response response = fileUtils.readStaticFile("./");

        assertEquals(response.statusCode(), _400_BAD_REQUEST);
    }

    /**
     * If we encounter a file we don't recognize, we'll label it as application/octet-stream.  Browsers
     * won't know what to do with this, so they will treat it as if the Content-Disposition header was set
     * to attachment, and propose a "Save As" dialog.  This will make it clearer when data has not
     * been labeled with a proper mime.
     */
    @Test
    public void test_FileUtils_Edge_ApplicationOctetStream() {
        var response = fileUtils.readStaticFile("Foo");
        assertEquals(response.extraHeaders().get("content-type"), "application/octet-stream");
    }

    /**
     * Users can add more mime types to our system by registering them
     * in the minum.config file in EXTRA_MIME_MAPPINGS.
     */
    @Test
    public void test_FileUtils_ExtraMimeMappings() {
        var input = List.of("png","image/png","wav","audio/wav");
        fileUtils.readExtraMappings(input);
        var mappings = fileUtils.getSuffixToMime();
        assertEquals(mappings.get("png"), "image/png");
        assertEquals(mappings.get("wav"), "audio/wav");
    }

    /**
     * while reading the extra mappings, bad syntax will cause a clear failure
     */
    @Test
    public void test_FileUtils_ExtraMimeMappings_BadSyntax() {
        var input = List.of("png","image/png","EXTRA_WORD_HERE","wav","audio/wav");
        var ex = assertThrows(InvariantException.class, () -> fileUtils.readExtraMappings(input));
        assertEquals(ex.getMessage(), "input must be even (key + value = 2 items). Your input: [png, image/png, EXTRA_WORD_HERE, wav, audio/wav]");
    }

    /**
     * If there's no values, it should work fine, it should simply not add any new mime mappings
     */
    @Test
    public void test_FileUtils_ExtraMimeMappings_NoValues() {
        var mappings = fileUtils.getSuffixToMime();
        int before = mappings.size();
        List<String> input = List.of();

        fileUtils.readExtraMappings(input);

        int after = mappings.size();
        assertEquals(before,after);
    }
}
