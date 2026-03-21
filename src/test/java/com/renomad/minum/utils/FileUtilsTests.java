package com.renomad.minum.utils;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.RegexUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.FileUtils.*;

public class FileUtilsTests {
    private static FileUtils fileUtils;
    private static TestLogger logger;
    private static Context context;


    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        Constants constants = context.getConstants();
        fileUtils = new FileUtils(logger, constants);
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
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
        var ex = assertThrows(UtilsException.class,
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

    /**
     * These are tests for what kind of paths we won't allow.  Basically,
     * the regex is looking for characters that request reading from parent
     * directories or alternate drives.
     */
    @Test
    public void test_BadFilePathPatterns() {
        assertThrows(InvariantException.class, "filename (../foo) contained invalid characters", () -> checkForBadFilePatterns("../foo"));
        assertThrows(InvariantException.class, "filename (foo/..) contained invalid characters", () -> checkForBadFilePatterns("foo/.."));
        assertThrows(InvariantException.class, "filename (:foo) contained invalid characters (:).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns(":foo"));
        assertThrows(InvariantException.class, "filename (foo:) contained invalid characters (:).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("foo:"));
        assertThrows(InvariantException.class, "filename (//foo) contained invalid characters", () -> checkForBadFilePatterns("//foo"));
        assertThrows(InvariantException.class, "filename (foo//) contained invalid characters", () -> checkForBadFilePatterns("foo//"));

        // an empty filename is a problem
        assertThrows(InvariantException.class, "filename was empty", () -> checkForBadFilePatterns(""));

        // having a forward or backward slash at the beginning is not alright
        assertThrows(InvariantException.class, "filename (/foo) contained invalid characters", () -> checkForBadFilePatterns("/foo"));
        assertThrows(InvariantException.class, "filename (\\foo) contained invalid characters", () -> checkForBadFilePatterns("\\foo"));
        // having invalid characters is disallowed.  For file names, the only characters
        // allowed are upper and lower case a-z ascii, numbers 0-9, dash,
        // period, forward and backward slash, and underscore
        assertThrows(InvariantException.class, "filename (a!1) contained invalid characters (!).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("a!1"));
        assertThrows(InvariantException.class, "filename (+) contained invalid characters (+).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("+"));
        assertThrows(InvariantException.class, "filename (=) contained invalid characters (=).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("="));
        assertThrows(InvariantException.class, "filename ($) contained invalid characters ($).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("$"));
        assertThrows(InvariantException.class, "filename (?) contained invalid characters (?).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("?"));
        assertThrows(InvariantException.class, "filename (naïve) contained invalid characters (ï).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("naïve"));

        // having a forward or backward slash in the midst is ok
        checkForBadFilePatterns("foo/bar");
        checkForBadFilePatterns("foo\\bar");

        // having valid chars is ok
        checkForBadFilePatterns("abcABC__.foo.-whatever");
    }

    @Test
    public void test_WithinDirectory() {
        checkFileIsWithinDirectory("resources/gettysburg_address.txt", "src/test");
        assertTrue(true, "should get here without an exception thrown");

        var result = assertThrows(InvariantException.class, () -> checkFileIsWithinDirectory("/", "src/test"));
        assertEquals(result.getMessage(), "path (/) was not within directory (src/test)");

        var result3 = assertThrows(InvariantException.class, () -> checkFileIsWithinDirectory("foobaz/foo", "src/test"));
        assertTrue(result3.getMessage().contains("java.nio.file.NoSuchFileException"));
    }

    @Test
    public void test_deleteDirectoryRecursivelyIfExists_EdgeCase_DirectoryNotExists() {
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("target/foo"));
        assertTrue(logger.doesMessageExist("system was requested to delete directory"));
    }

    @Test
    public void test_deleteDirectoryRecursivelyIfExists_EdgeCase_HappyPath() throws IOException {
        fileUtils.makeDirectory(Path.of("target/testing_delete_directory/foo"));
        Files.writeString(Path.of("target/testing_delete_directory/foo/bar.txt"), "hello there, this is a test file");
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("target/testing_delete_directory"));
        assertFalse(Files.exists(Path.of("target/testing_delete_directory")));
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
        FileUtils fileUtils = new FileUtils(logger, throwingFileReader);
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
        FileUtils fileUtils = new FileUtils(logger, throwingFileReader);
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
        FileUtils fileUtils = new FileUtils(logger, throwingFileReader);
        assertThrows(UtilsException.class,
                "Error during deleteDirectoryRecursivelyIfExists: java.nio.file.NoSuchFileException: foofoo",
                () -> fileUtils.walkPathDeleting(Path.of("foofoo")));
    }

    @Test
    public void test_innerCreateDirectory() {
        assertThrows(UtilsException.class,
                "java.lang.NullPointerException: Cannot invoke \"java.nio.file.Path.getFileSystem()\" because \"path\" is null",
                () -> FileUtils.innerCreateDirectory(null));
    }

    /**
     * This method exists as a security guardrail, when the file
     * being requested is influenced by a user, and therefore untrusted.
     */
    @Test
    public void test_SafeResolve() {
        safeResolve("src/test", "java");
        safeResolve("src/test", "resources/kitty.jpg");
        assertThrows(InvariantException.class, () -> safeResolve("src/test", "/"));
        assertThrows(InvariantException.class, () -> safeResolve("src/test", "../../docs"));
    }

    /**
     * Generative (fuzzing) test for path traversal and invalid characters.
     * This test generates a variety of potentially malicious or malformed strings
     * to ensure that {@link FileUtils#checkForBadFilePatterns(String)} consistently
     * identifies and rejects them, protecting against path traversal and other
     * file-system based attacks.
     */
    @Test
    public void test_BadFilePathPatterns_Generative() {
        List<String> badPatterns = List.of(
                "../", "/..", "..\\", "\\..",
                "//", ":", "*", "?", "\"", "<", ">", "|",
                "\0", "\n", "\r", "\t",
                "%2e%2e%2f", "%2e%2e%5c", "%2f%2e%2e", "%5c%2e%2e", // URL encoded path traversal
                " ", "  ", "\u00A0", // Whitespace and non-breaking space
                "наивный", "测试", "😀" // Non-ascii characters
        );

        for (String pattern : badPatterns) {
            assertThrows(InvariantException.class, () -> checkForBadFilePatterns(pattern));
        }

        // Test random combinations
        java.util.Random random = new java.util.Random(42); // Fixed seed for reproducibility
        String allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-/\\";
        for (int i = 0; i < 100; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 20; j++) {
                if (random.nextDouble() < 0.1) {
                    sb.append(badPatterns.get(random.nextInt(badPatterns.size())));
                } else {
                    sb.append(allowedChars.charAt(random.nextInt(allowedChars.length())));
                }
            }
            String candidate = sb.toString();
            try {
                checkForBadFilePatterns(candidate);
            } catch (InvariantException ex) {
                // Expected for many generated strings
            } catch (Exception ex) {
                assertTrue(false, "Generated string \"" + candidate + "\" caused an unexpected exception: " + ex);
            }
        }
    }
}
