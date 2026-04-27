package com.renomad.minum.utils;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.RegexUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.FileUtils.*;

public class FileUtilsTests {
    private static FileUtils fileUtils;
    private static TestLogger logger;
    private static Context context;

    /**
     * A directory in which we can work for this test suite
     */
    private static Path overallTestDirectory;

    @BeforeClass
    public static void init() throws IOException {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        Constants constants = context.getConstants();
        fileUtils = new FileUtils(logger, constants);
        overallTestDirectory = Path.of("out/test_directory/");
        fileUtils.deleteDirectoryRecursivelyIfExists(overallTestDirectory);
        fileUtils.innerCreateDirectory(overallTestDirectory);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        shutdownTestingContext(context);
        fileUtils.deleteDirectoryRecursivelyIfExists(overallTestDirectory);
    }

    @Test
    public void test_WriteString_EmptyPath() {
        var ex = assertThrows(UtilsException.class, () -> fileUtils.writeString(Path.of(""), "bar"));
        assertEquals(ex.getMessage(), "an empty path was provided to writeString");
    }

    @Test
    public void test_ReadString_EmptyPath() {
        var ex = assertThrows(UtilsException.class, () -> fileUtils.readString(Path.of("")));
        assertEquals(ex.getMessage(), "an empty path was provided to readString");
    }

    /**
     * This will fail because we don't have a directory of
     * target/foo/bar
     */
    @Test
    public void test_WriteString_IOException() {
        assertThrows(NoSuchFileException.class, () -> fileUtils.writeString(Path.of("target/foo/bar"), "baz"));
    }

    /**
     * This will fail because we don't have a directory of
     * target/foo/bar
     */
    @Test
    public void test_ReadString_IOException() {
        assertThrows(NoSuchFileException.class, () -> fileUtils.readString(Path.of("target/foo/bar")));
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
        assertThrows(ForbiddenUseException.class, "filename (../foo) contained invalid characters", () -> checkForBadFilePatterns("../foo"));
        assertThrows(ForbiddenUseException.class, "filename (foo/..) contained invalid characters", () -> checkForBadFilePatterns("foo/.."));
        assertThrows(ForbiddenUseException.class, "filename (:foo) contained invalid characters (:).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns(":foo"));
        assertThrows(ForbiddenUseException.class, "filename (foo:) contained invalid characters (:).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("foo:"));
        assertThrows(ForbiddenUseException.class, "filename (//foo) contained invalid characters", () -> checkForBadFilePatterns("//foo"));
        assertThrows(ForbiddenUseException.class, "filename (foo//) contained invalid characters", () -> checkForBadFilePatterns("foo//"));

        // an empty filename is a problem
        assertThrows(IllegalArgumentException.class, "path was blank", () -> checkForBadFilePatterns(""));

        // having a forward or backward slash at the beginning is not alright
        assertThrows(ForbiddenUseException.class, "filename (/foo) contained invalid characters", () -> checkForBadFilePatterns("/foo"));
        assertThrows(ForbiddenUseException.class, "filename (\\foo) contained invalid characters", () -> checkForBadFilePatterns("\\foo"));
        // having invalid characters is disallowed.  For file names, the only characters
        // allowed are upper and lower case a-z ascii, numbers 0-9, dash,
        // period, forward and backward slash, and underscore
        assertThrows(ForbiddenUseException.class, "filename (a!1) contained invalid characters (!).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("a!1"));
        assertThrows(ForbiddenUseException.class, "filename (+) contained invalid characters (+).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("+"));
        assertThrows(ForbiddenUseException.class, "filename (=) contained invalid characters (=).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("="));
        assertThrows(ForbiddenUseException.class, "filename ($) contained invalid characters ($).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("$"));
        assertThrows(ForbiddenUseException.class, "filename (?) contained invalid characters (?).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("?"));
        assertThrows(ForbiddenUseException.class, "filename (naïve) contained invalid characters (ï).  Allowable characters are alpha-numeric ascii both cases, underscore, forward and backward-slash, period, and dash", () -> checkForBadFilePatterns("naïve"));

        // having a forward or backward slash in the midst is ok
        checkForBadFilePatterns("foo/bar");
        checkForBadFilePatterns("foo\\bar");

        // having valid chars is ok
        checkForBadFilePatterns("abcABC__.foo.-whatever");
    }

    @Test
    public void test_WithinDirectory() throws IOException {
        fileUtils.checkFileIsWithinDirectory("resources/gettysburg_address.txt", "src/test");
        assertTrue(true, "should get here without an exception thrown");

        var result = assertThrows(ForbiddenUseException.class, () -> fileUtils.checkFileIsWithinDirectory("/", "src/test"));
        assertEquals(result.getMessage(), "path (/) was not within directory (src/test)");

        assertThrows(NoSuchFileException.class, () -> fileUtils.checkFileIsWithinDirectory("foobaz/foo", "src/test"));
    }

    @Test
    public void test_deleteDirectoryRecursivelyIfExists_EdgeCase_DirectoryNotExists() throws IOException {
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
        assertThrows(FileNotFoundException.class, () -> fileUtils.readBinaryFile("target/does_not_exist.txt"));
    }

    @Test
    public void test_ReadBinaryFile_IOException() {
        FileUtils fileUtils = new FileUtils(logger, throwingFileReader);
        var ex = assertThrows(UtilsException.class, () -> fileUtils.readBinaryFile("foo"));
        assertEquals(ex.getMessage(), "Testing");
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
        assertThrows(FileNotFoundException.class, () -> fileUtils.readTextFile("target/does_not_exist.txt"));
    }

    @Test
    public void test_ReadTextFile_IOException() {
        FileUtils fileUtils = new FileUtils(logger, throwingFileReader);
        var ex = assertThrows(UtilsException.class, () -> fileUtils.readTextFile("foo"));
        assertEquals(ex.getMessage(), "Testing");
    }

    /**
     * A {@link FileReader} that always throws an IOException
     */
    IFileReader throwingFileReader = path -> {
        throw new UtilsException("Testing");
    };

    @Test
    public void test_walkPathDeleting() {
        FileUtils fileUtils = new FileUtils(logger, throwingFileReader);
        assertThrows(NoSuchFileException.class, () -> fileUtils.walkPathDeleting(Path.of("foofoo")));
    }

    @Test
    public void test_innerCreateDirectory_disallowedNull() {
        assertThrows(IllegalArgumentException.class,
                "directory parameter is disallowed to be null when creating a directory",
                () -> fileUtils.innerCreateDirectory(null));
    }

    /**
     * An {@link IOException} will be thrown by Files.createDirectories
     * if we ask it to write a directory over an existing one.
     */
    @Test
    public void test_innerCreateDirectory_IOException() throws IOException {
        Path innerPath = overallTestDirectory.resolve("test_innerCreateDirectory_IOException");
        Files.createFile(innerPath);
        assertThrows(FileAlreadyExistsException.class, () -> fileUtils.innerCreateDirectory(innerPath));
    }

    /**
     * This method exists as a security guardrail, when the file
     * being requested is influenced by a user, and therefore untrusted.
     */
    @Test
    public void test_SafeResolve() throws IOException {
        fileUtils.safeResolve("src/test", "java");
        fileUtils.safeResolve("src/test", "resources/kitty.jpg");
        assertThrows(ForbiddenUseException.class, () -> fileUtils.safeResolve("src/test", "/"));
        assertThrows(ForbiddenUseException.class, () -> fileUtils.safeResolve("src/test", "../../docs"));
    }

    /**
     * Just getting a handle on the delete method
     */
    @Test
    public void test_delete() throws IOException {
        Path innerPath = overallTestDirectory.resolve("testing_delete");
        Files.createFile(innerPath);
        fileUtils.delete(innerPath);

        // poof! It should be gone
        assertFalse(Files.exists(innerPath));

        // now let's try to delete it again (should throw exception)
        assertThrows(NoSuchFileException.class, () -> fileUtils.delete(innerPath));
    }
}
