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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static com.renomad.minum.testing.TestFramework.*;

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
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of("../foo"));
        assertTrue(logger.doesMessageExist("Bad path requested at deleteDirectoryRecursivelyIfExists"));
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
}
