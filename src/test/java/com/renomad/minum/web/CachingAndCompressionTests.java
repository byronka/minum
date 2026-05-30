package com.renomad.minum.web;

import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.TestLoggerException;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FakeFileUtils;
import com.renomad.minum.utils.IFileReader;
import com.renomad.minum.utils.LRUCache;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * The caching and compression can be a little tricky, so creating a
 * separate class to focus on it.
 */
public class CachingAndCompressionTests {

    private static ZonedDateTime defaultTestTime = ZonedDateTime.of(2026, 1, 1, 1, 1, 1, 1, ZoneId.of("UTC"));

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("CachingAndCompressionTests");
        logger = (TestLogger) context.getLogger();
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


    /**
     * If the user requests a static file that is empty,
     * the system will return a 200 with a content-length of
     * zero, and will note that the file is empty in the logs.
     */
    @Test
    public void testStaticFileReadEmptyData() {
        // enable TRACE logging so we can assert on the log
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, true);

        // configure our mock FileUtils to get us to the testing spot
        var fileUtils = new FakeFileUtils();
        fileUtils.isRegularFileValue = true;

        // configure our mock filereader to get us where we want to test
        var fileReader = new IFileReader() {
            @Override public byte[] readFile(String path) throws IOException {return new byte[0];}
            @Override public ReentrantLock getCacheLock() {return new ReentrantLock();}
            @Override public Map<String, byte[]> getLruCache() {return Map.of();}
        };
        // load our webframework with our mocks
        var webFramework = new WebFramework(context, defaultTestTime, fileReader, fileUtils);

        webFramework.readStaticFile("foo", new Headers(List.of("accept: gzip")));

        assertTrue(logger.doesMessageExist("Returning 200 OK, content-length 0, with mime of application/octet-stream"));

        // configure not to show TRACE anymore for following tests.
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, false);
    }

    /*
    Minum reads static files differently depending on two characteristics:
       * size (less than WebFramework.MAX_CACHED_BYTES gets cached, larger uses a FileChannel response, 0 bytes has a simple response)
       * compressibility (if compressible i.e. can be compressed at least 70% of original size) - will cache the file path as a compressible path
    */

    /**
     * Test that a file will be read from disk the first time, then from cache the second,
     * if it is smaller than WebFramework.MAX_CACHED_BYTES.
     * We also test that the system checks compressibility the first time around,
     * and then has that information cached the second time.
     */
    @Test
    public void testStaticFileCaching() {
        Map<String, byte[]> fileCache = LRUCache.getLruCache();
        // enable TRACE logging so we can assert on the log
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, true);

        // configure our mock FileUtils to get us to the testing spot
        var fileUtils = new FakeFileUtils();
        fileUtils.isRegularFileValue = true;
        fileUtils.sizeValue = 100;

        // configure our mock filereader to get us where we want to test
        var fileReader = new IFileReader() {
            @Override public byte[] readFile(String path) throws IOException {
                var result = new byte[100];
                // put the data in two forms, to handle this test running on different
                // operating systems
                fileCache.put("src\\test\\webapp\\static\\foo", result);
                fileCache.put("src/test/webapp/static/foo", result);
                return result;
            }
            @Override public ReentrantLock getCacheLock() {return new ReentrantLock();}
            @Override public Map<String, byte[]> getLruCache() {return fileCache;}
        };
        // load our webframework with mocks
        var webFramework = new WebFramework(context, defaultTestTime, fileReader, fileUtils);

        // here we go.
        webFramework.readStaticFile("foo", new Headers(List.of("accept: gzip")));

        // confirm we do see the check for compressibility
        assertTrue(logger.doesMessageExist("worth compressing? true.  Compression ratio: 24%"));

        // confirm we do not see the system using a cached value at this point - here, readFile
        // has not yet run, so the data is not yet in the cache.
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("creating an OK HTTP response for 100 bytes of data found in cache"));

        // see comment below - adding fake log entries to make assertion on log
        // statements more consistent.
        for (int i = 0; i < 25; i++) {
            logger.logDebug(() -> "ADDING FAKE LOG MESSAGE, THIS IS FOR TESTING");
        }

        // when we load this data a second time, it uses the cache, and does not
        // check whether the data is compressible.  In order to test this cleanly,
        // the test adds some fake log statements so the log search won't find the previous
        // log entry where we checked.
        webFramework.readStaticFile("foo", new Headers(List.of("accept: gzip")));

        // confirm that this time around, we aren't checking whether the file is compressible,
        // we already know it.
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("worth compressing? true."));

        // confirm that this time around, the system has decided to pull data from the cache instead
        // of reading from the disk (although we're faking the disk part for this test).
        assertTrue(logger.doesMessageExist("100 bytes of data found in cache for request"));
        assertTrue(logger.doesMessageExist("length: 100, fileIsCompressible: true"));

        // configure not to show TRACE anymore for following tests.
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, false);
    }

    /**
     * The first time through we check its compressibility, and
     * the second time we already know it is NOT compressible.
     */
    @Test
    public void testStaticFileCompressed() {
        // enable TRACE logging so we can assert on the log
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, true);

        // configure our mock FileUtils to get us to the testing spot
        var fileUtils = new FakeFileUtils();
        fileUtils.isRegularFileValue = true;
        fileUtils.sizeValue = 1;

        // configure our mock filereader to get us where we want to test
        var fileReader = new IFileReader() {
            @Override public byte[] readFile(String path) throws IOException {
                // one byte of content, after zipping, will be like 28 bytes, so
                // our system will decide this data is not compressible.
                return new byte[1];
            }
            @Override public ReentrantLock getCacheLock() {return new ReentrantLock();}
            @Override public Map<String, byte[]> getLruCache() {return LRUCache.getLruCache();}
        };
        // load our webframework with mocks
        var webFramework = new WebFramework(context, defaultTestTime, fileReader, fileUtils);

        // here we go.
        webFramework.readStaticFile("foo", new Headers(List.of("accept: gzip")));

        // confirm we do see the check for compressibility
        assertTrue(logger.doesMessageExist("worth compressing? false."));

        // see comment below - adding fake log entries to make assertion on log
        // statements more consistent.
        for (int i = 0; i < 25; i++) {
            logger.logDebug(() -> "ADDING FAKE LOG MESSAGE, THIS IS FOR TESTING");
        }

        // when we load this data a second time, it uses the cache, and does not
        // check whether the data is compressible.  In order to test this cleanly,
        // the test adds some fake log statements so the log search won't find the previous
        // log entry where we checked.
        webFramework.readStaticFile("foo", new Headers(List.of("accept: gzip")));

        // confirm that this time around, we aren't checking whether the file is compressible,
        // we already know it.
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("worth compressing?"));
        assertTrue(logger.doesMessageExist("length: 1, fileIsCompressible: false"));

        // configure not to show TRACE anymore for following tests.
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, false);
    }

    /**
     * For a large file (greater-than-or-equal to WebFramework.MAX_CACHED_BYTES) there is no caching,
     * and no compression (if larger file, ought to already be compressed.  We don't use chunked-transfer
     * encoding, and we don't want to keep a large file in memory for compressing).
     */
    @Test
    public void testStaticFileLarge() {
        // enable TRACE logging so we can assert on the log
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, true);

        // configure our mock FileUtils to get us to the testing spot
        var fileUtils = new FakeFileUtils();
        fileUtils.isRegularFileValue = true;

        // this is a key ingredient of the test - setting the file size to the
        // value at which the system will determine it not to be cacheable
        // or compressible.
        fileUtils.sizeValue = WebFramework.MAX_CACHED_BYTES;

        // configure our mock filereader to get us where we want to test
        var fileReader = new IFileReader() {
            @Override public byte[] readFile(String path) throws IOException {
                // one byte of content, after zipping, will be like 28 bytes, so
                // our system will decide this data is not compressible.
                return new byte[1];
            }
            @Override public ReentrantLock getCacheLock() {return new ReentrantLock();}
            @Override public Map<String, byte[]> getLruCache() {return LRUCache.getLruCache();}
        };
        // load our webframework with mocks
        var webFramework = new WebFramework(context, defaultTestTime, fileReader, fileUtils);

        // here we go.
        webFramework.readStaticFile("foo", new Headers(List.of("accept: gzip")));

        // confirm we do see the check for compressibility
        assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("worth compressing?"));
        assertTrue(logger.doesMessageExist("Since greater than max allowed (100000), no caching allowed."));

        // configure not to show TRACE anymore for following tests.
        context.getLogger().getActiveLogLevels().put(LoggingLevel.TRACE, false);
    }

}
