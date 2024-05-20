package com.renomad.minum.utils;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.RegexUtils;
import com.renomad.minum.web.Response;
import com.renomad.minum.web.StatusLine;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_400_BAD_REQUEST;
import static com.renomad.minum.web.StatusLine.StatusCode.CODE_404_NOT_FOUND;

public class FileUtilsTests {
    private static FileUtils fileUtils;
    private static TestLogger logger;
    private static Context context;
    private static Constants constants;


    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        constants = context.getConstants();
        fileUtils = new FileUtils(logger, constants);
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }


    @Test
    public void test_readStaticFile_CSS() {
        Response response = fileUtils.readStaticFile("main.css");

        assertEquals(response.statusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertTrue(response.body().length > 0);
        assertEquals(response.extraHeaders().get("content-type"), "text/css");
    }

    @Test
    public void test_readStaticFile_JS() {
        Response response = fileUtils.readStaticFile("index.js");

        assertEquals(response.statusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertTrue(response.body().length > 0);
        assertEquals(response.extraHeaders().get("content-type"), "application/javascript");
    }

    @Test
    public void test_readStaticFile_HTML() {
        Response response = fileUtils.readStaticFile("index.html");

        assertEquals(response.statusCode(), StatusLine.StatusCode.CODE_200_OK);
        assertTrue(response.body().length > 0);
        assertEquals(response.extraHeaders().get("content-type"), "text/html");
    }

    /**
     * If a user requests a file with .. in front, that means go up
     * a directory - we don't really want that happening.
     */
    @Test
    public void test_readStaticFile_Edge_OutsideDirectory() {
        Response response = fileUtils.readStaticFile("../templates/auth/login_page_template.html");

        assertEquals(response.statusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * Edge case - forward slashes
     */
    @Test
    public void test_ReadFile_Edge_ForwardSlashes() {
        Response response = fileUtils.readStaticFile("//index.html");

        assertEquals(response.statusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * Edge case - colon
     */
    @Test
    public void test_readStaticFile_Edge_Colon() {
        Response response = fileUtils.readStaticFile(":");

        assertEquals(response.statusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * Edge case - a directory
     */
    @Test
    public void test_readStaticFile_Edge_Directory() {
        Response response = fileUtils.readStaticFile("src/test/resources/");

        assertEquals(response.statusCode(), CODE_404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_readStaticFile_Edge_CurrentDirectory() {
        Response response = fileUtils.readStaticFile("./");

        assertEquals(response.statusCode(), CODE_404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_readStaticFile_EdgeCase() {
        Response response = fileUtils.readStaticFile("./");

        assertEquals(response.statusCode(), CODE_404_NOT_FOUND);
    }

    /**
     * Edge case - current directory
     */
    @Test
    public void test_readStaticFile_IOException() {
        FileUtils fileUtils = new FileUtils(logger, constants, throwingFileReader);
        Response response = fileUtils.readStaticFile("foo");

        assertEquals(response.statusCode(), CODE_400_BAD_REQUEST);
    }

    /**
     * If we encounter a file we don't recognize, we'll label it as application/octet-stream.  Browsers
     * won't know what to do with this, so they will treat it as if the Content-Disposition header was set
     * to attachment, and propose a "Save As" dialog.  This will make it clearer when data has not
     * been labeled with a proper mime.
     */
    @Test
    public void test_Edge_ApplicationOctetStream() {
        var response = fileUtils.readStaticFile("Foo");
        assertEquals(response.extraHeaders().get("content-type"), "application/octet-stream");
    }

    /**
     * Users can add more mime types to our system by registering them
     * in the minum.config file in EXTRA_MIME_MAPPINGS.
     */
    @Test
    public void test_ExtraMimeMappings() {
        var input = List.of("png","image/png","wav","audio/wav");
        fileUtils.readExtraMimeMappings(input);
        var mappings = fileUtils.getSuffixToMime();
        assertEquals(mappings.get("png"), "image/png");
        assertEquals(mappings.get("wav"), "audio/wav");
    }

    /**
     * while reading the extra mappings, bad syntax will cause a clear failure
     */
    @Test
    public void test_ExtraMimeMappings_BadSyntax() {
        var input = List.of("png","image/png","EXTRA_WORD_HERE","wav","audio/wav");
        var ex = assertThrows(InvariantException.class, () -> fileUtils.readExtraMimeMappings(input));
        assertEquals(ex.getMessage(), "input must be even (key + value = 2 items). Your input: [png, image/png, EXTRA_WORD_HERE, wav, audio/wav]");
    }

    /**
     * If there's no values, it should work fine, it should simply not add any new mime mappings
     */
    @Test
    public void test_ExtraMimeMappings_NoValues() {
        var mappings = fileUtils.getSuffixToMime();
        int before = mappings.size();
        List<String> input = List.of();

        fileUtils.readExtraMimeMappings(input);

        int after = mappings.size();
        assertEquals(before,after);
    }

    @Test
    public void test_ExtraMimeMappings_Null() {
        var mappings = fileUtils.getSuffixToMime();
        int before = mappings.size();

        fileUtils.readExtraMimeMappings(null);

        int after = mappings.size();
        assertEquals(before,after);
    }

    /**
     * The user cannot write to a parent directory. If they
     * try, a log message is entered about it, but nothing
     * happens - we return, having done nothing.
     */
    @Test
    public void test_WriteString_preventWritingToParentDirectory() {
        fileUtils.writeString(Path.of("../foo"), "bar");
        assertTrue(logger.doesMessageExist("Bad path requested at writeString"));
    }

    @Test
    public void test_WriteString_EmptyPath() {
        fileUtils.writeString(Path.of(""), "bar");
        assertTrue(logger.doesMessageExist("an empty path was provided to writeString"));
    }

    /**
     * This will fail because we don't have a directory of
     * target/foo/bar
     */
    @Test
    public void test_WriteString_IOException() {
        var ex = assertThrows(RuntimeException.class,
                () -> fileUtils.writeString(Path.of("target/foo/bar"), "baz"));
        assertTrue(RegexUtils.isFound("java.nio.file.NoSuchFileException: target.foo.bar", ex.getMessage()));
    }

    @Test
    public void test_WriteString_HappyPath() throws IOException {
        Files.deleteIfExists(Path.of("target/foo"));

        fileUtils.writeString(Path.of("target/foo"), "bar");

        assertTrue(Files.exists(Path.of("target/foo")));
        Files.deleteIfExists(Path.of("target/foo"));
    }

    @Test
    public void test_BadFilePathPatterns() {
        String regex = FileUtils.badFilePathPatterns.toString();
        String firstResult = RegexUtils.find(regex, "../foo");
        assertEquals(firstResult, "..");
        String secondResult = RegexUtils.find(regex, ":foo");
        assertEquals(secondResult, ":");
        String thirdResult = RegexUtils.find(regex, "//foo");
        assertEquals(thirdResult, "//");
    }

    @Test
    public void test_deleteDirectoryRecursivelyIfExists_EdgeCase_BadPattern() {
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("../foo"), logger);
        assertTrue(logger.doesMessageExist("Bad path requested at deleteDirectoryRecursivelyIfExists"));
    }

    @Test
    public void test_deleteDirectoryRecursivelyIfExists_EdgeCase_DirectoryNotExists() {
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("target/foo"), logger);
        assertTrue(logger.doesMessageExist("system was requested to delete directory"));
    }
    @Test
    public void test_deleteDirectoryRecursivelyIfExists_EdgeCase_HappyPath() throws IOException {
        fileUtils.makeDirectory(Path.of("target/testing_delete_directory/foo"));
        Files.writeString(Path.of("target/testing_delete_directory/foo/bar.txt"), "hello there, this is a test file");
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("target/testing_delete_directory"), logger);
        assertFalse(Files.exists(Path.of("target/testing_delete_directory")));
    }

    @Test
    public void test_MakeDirectory_EdgeCase_BadPath() {
        fileUtils.makeDirectory(Path.of("../foo"));
        assertTrue(logger.doesMessageExist("Bad path requested at makeDirectory"));
    }

    @Test
    public void test_ReadBinaryFile() throws IOException {
        Path path = Path.of("target/test_ReadBinaryFile.txt");
        Files.deleteIfExists(path);
        Files.writeString(path, "hello");
        byte[] bytes = fileUtils.readBinaryFile("target/test_ReadBinaryFile.txt");
        assertEqualByteArray(bytes, "hello".getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(path);
    }

    @Test
    public void test_ReadBinaryFile_FileMissing() {
        byte[] bytes = fileUtils.readBinaryFile("target/does_not_exist.txt");
        assertEqualByteArray(bytes, new byte[0]);
    }

    @Test
    public void test_ReadBinaryFile_IOException() {
        FileUtils fileUtils = new FileUtils(logger, constants, throwingFileReader);
        byte[] bytes = fileUtils.readBinaryFile("foo");
        assertEqualByteArray(bytes, new byte[0]);
        assertTrue(logger.doesMessageExist("Error while reading file foo, returning empty byte array. java.io.IOException: Testing"));
    }

    @Test
    public void test_ReadTextFile() throws IOException {
        Path path = Path.of("target/test_ReadTextFile.txt");
        Files.deleteIfExists(path);
        Files.writeString(path, "hello");
        String string = fileUtils.readTextFile("target/test_ReadTextFile.txt");
        assertEquals(string, "hello");
        Files.deleteIfExists(path);
    }

    @Test
    public void test_ReadTextFile_FileMissing() {
        String string = fileUtils.readTextFile("target/does_not_exist.txt");
        assertEquals(string, "");
    }

    @Test
    public void test_ReadTextFile_IOException() {
        FileUtils fileUtils = new FileUtils(logger, constants, throwingFileReader);
        String string = fileUtils.readTextFile("foo");
        assertEquals(string, "");
        assertTrue(logger.doesMessageExist("Error while reading file foo, returning empty string. java.io.IOException: Testing"));
    }

    /**
     * A {@link FileReader} that always throws an IOException
     */
    IFileReader throwingFileReader = path -> {
        throw new IOException("Testing");
    };

    @Test
    public void test_walkPathDeleting() {
        assertThrows(UtilsException.class,
                "Error during deleteDirectoryRecursivelyIfExists: java.nio.file.NoSuchFileException: foofoo",
                () -> FileUtils.walkPathDeleting(Path.of("foofoo"), logger));
    }

    @Test
    public void test_innerCreateDirectory() {
        assertThrows(UtilsException.class,
                "java.lang.NullPointerException: Cannot invoke \"java.nio.file.Path.getFileSystem()\" because \"path\" is null",
                () -> FileUtils.innerCreateDirectory(null));
    }
}
