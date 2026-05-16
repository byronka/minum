package com.renomad.minum.utils;

import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.renomad.minum.testing.TestFramework.*;

public class FileReaderTests {
    private static TestLogger logger;
    private static Map<String, byte[]> lruCache;
    private static Context context;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        Constants constants = context.getConstants();
        lruCache = LRUCache.getLruCache(constants.maxElementsLruCacheStaticFiles);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void test_ReadFile_EmptyFile() throws IOException {
        Path path = Path.of("target/testingreadfile.txt");
        Files.deleteIfExists(path);
        Files.writeString(path, "");
        var fileReader = new FileReader(lruCache, true, logger);
        byte[] bytes = fileReader.readFile("target/testingreadfile.txt");
        assertEqualByteArray(bytes, new byte[0]);
        Files.deleteIfExists(path);
    }

    @Test
    public void test_ReadFile_BadPath() {
        var fileReader = new FileReader(lruCache, true, logger);
        var ex = assertThrows(ForbiddenUseException.class, () -> fileReader.readFile("../testingreadfile.txt"));
        assertEquals(ex.getMessage(), "filename (../testingreadfile.txt) contained invalid characters");
    }

    @Test
    public void test_ReadFile_InCache() throws IOException {
        byte[] value = {1, 2, 3};
        lruCache.put("testingreadfile.txt", value);
        var fileReader = new FileReader(lruCache, true, logger);
        byte[] bytes = fileReader.readFile("testingreadfile.txt");
        ReentrantLock cacheLock = fileReader.getCacheLock();
        cacheLock.lock();
        byte[] result;
        try {
            result = lruCache.get("testingreadfile.txt");
        } finally {
            cacheLock.unlock();
        }
        assertEqualByteArray(bytes, value);
        assertEqualByteArray(bytes, result);
    }

    @Test
    public void test_ReadFile_NoCache() throws IOException {
        Path path = Path.of("target/testingreadfile.txt");
        Files.deleteIfExists(path);
        Files.writeString(path, "Hello test!");
        var fileReader = new FileReader(lruCache, false, logger);
        byte[] bytes = fileReader.readFile("target/testingreadfile.txt");
        assertEquals(new String(bytes, StandardCharsets.UTF_8), "Hello test!");
        Files.deleteIfExists(path);
    }

    @Test
    public void test_readTheFile_NoFileFound() {
        var fileReader = new FileReader(lruCache, false, logger);
        assertThrows(FileNotFoundException.class, () -> fileReader.readTheFile("target/wahooooo.txt", logger, false, lruCache));
    }

    /**
     * It is possible to put more than one value to a key in the
     * cache, in which case the logging will show different results
     */
    @Test
    public void test_readTheFile_AddSecondEntryToCacheSameKey() throws IOException {
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, true);
        var fileReader = new FileReader(lruCache, true, logger);
        Path path = Path.of("src/test/resources/gettysburg_address.txt");

        byte[] bytes = fileReader.readTheFile(path.toString(), logger, true, lruCache);
        assertEquals(Files.readString(path), new String(bytes));
        assertTrue(logger.doesMessageExist("No previous value for this key existed"));

        byte[] bytes2 = fileReader.readTheFile(path.toString(), logger, true, lruCache);
        assertEquals(Files.readString(path), new String(bytes2));
        assertTrue(logger.doesMessageExist("The previous length of data for this key was 1510 bytes"));

        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, false);
    }

}
