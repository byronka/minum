package com.renomad.minum.utils;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class FileReaderTests {
    private static TestLogger logger;
    private Map<String, byte[]> lruCache;
    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
        Constants constants = context.getConstants();
        lruCache = LRUCache.getLruCache(constants.maxElementsLruCacheStaticFiles);
    }

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
    public void test_ReadFile_BadPath() throws IOException {
        var fileReader = new FileReader(lruCache, true, logger);
        byte[] bytes = fileReader.readFile("../testingreadfile.txt");
        assertEqualByteArray(bytes, new byte[0]);
        assertTrue(logger.doesMessageExist("Bad path requested at readFile: ../testingreadfile.txt"));
    }

    @Test
    public void test_ReadFile_InCache() throws IOException {
        byte[] value = {1, 2, 3};
        lruCache.put("testingreadfile.txt", value);
        var fileReader = new FileReader(lruCache, true, logger);
        byte[] bytes = fileReader.readFile("testingreadfile.txt");
        assertEqualByteArray(bytes, value);
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
        assertThrows(FileNotFoundException.class, () -> FileReader.readTheFile("target/wahooooo.txt", logger, false, lruCache));
    }

}
