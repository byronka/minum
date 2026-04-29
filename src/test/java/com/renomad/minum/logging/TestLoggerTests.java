package com.renomad.minum.logging;

import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.MyThread;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static com.renomad.minum.logging.TestLogger.MAX_CACHE_SIZE;
import static com.renomad.minum.testing.TestFramework.*;

public class TestLoggerTests {


    static private IFileUtils fileUtils;
    static private Context context;
    static private TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("TestLoggerTests");
        fileUtils = new FileUtils(context.getLogger(), context.getConstants());
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    @Test
    public void test_TestLogger_MaxLines() {
        assertThrows(TestLoggerException.class,
                "Can only get up to "+MAX_CACHE_SIZE+" lines from the log",
                () -> logger.findFirstMessageThatContains("foo", MAX_CACHE_SIZE+1));
    }

    @Test
    public void test_TestLogger_MultipleLogEntriesFound() {
        logger.logDebug(() -> "foo");
        logger.logDebug(() -> "foo");
        var ex = assertThrows(TestLoggerException.class,
                () -> logger.findFirstMessageThatContains("foo"));
        assertTrue(ex.getMessage().contains("multiple values of foo found in:"));
    }

    /**
     * Not a whole lot to test here, other than to show
     * that sending a null value will just put a message
     * in the logs
     */
    @Test
    public void test_TestLogger_NullMessage() {
        logger.logDebug(() -> null);
        assertTrue(logger.doesMessageExist("(null message)"));
    }

    @Test
    public void test_TestLogger_ExceptionThrown() {
        logger.logDebug(() -> {
            throw new Exception("testing");
        });
        assertTrue(logger.doesMessageExist("EXCEPTION DURING GET: java.lang.Exception: testing"));
    }

    /**
     * This is a test of the test utility {@link TestLogger#findFirstMessageThatContains(String)}.
     * It allows us to examine the past log messges for something containing a string.  This comes
     * in handy for examining error message and confirming state changes.
     */
    @Test
    public void test_findMessage() {
        ArrayBlockingQueue<String> recentLogLines = new ArrayBlockingQueue<>(20);
        recentLogLines.offer("word1");
        recentLogLines.offer("word2");
        recentLogLines.offer("word3");
        List<String> foundWords = TestLogger.findMessage("word2", 3, recentLogLines);
        assertEquals(foundWords.getFirst(), "word2");
    }

    @Test
    public void test_findMessage_EdgeCase_WordNotFound() {
        ArrayBlockingQueue<String> recentLogLines = new ArrayBlockingQueue<>(20);
        recentLogLines.offer("word1");
        recentLogLines.offer("word2");
        recentLogLines.offer("word3");
        assertEquals(TestLogger.findMessage("foo", 3, recentLogLines), List.of());
    }

    @Test
    public void test_findMessage_EdgeCase_TooFewLinesRequested() {
        ArrayBlockingQueue<String> recentLogLines = new ArrayBlockingQueue<>(20);
        recentLogLines.offer("word1");
        recentLogLines.offer("word2");
        recentLogLines.offer("word3");
        assertEquals(TestLogger.findMessage("word2", 1, recentLogLines), List.of());
    }

    @Test
    public void test_findMessage_EdgeCase_NegativeValue() {
        ArrayBlockingQueue<String> recentLogLines = new ArrayBlockingQueue<>(20);
        recentLogLines.offer("word1");
        recentLogLines.offer("word2");
        recentLogLines.offer("word3");
        assertThrows(TestLoggerException.class,
                "number of recent log lines must be a positive number",
                () -> TestLogger.findMessage("word2", -1, recentLogLines));
    }

    @Test
    public void test_findMessage_EdgeCase_NoLogs() {
        ArrayBlockingQueue<String> recentLogLines = new ArrayBlockingQueue<>(20);
        assertThrows(TestLoggerException.class,
                "number of recent log lines must be a positive number",
                () -> TestLogger.findMessage("word2", -1, recentLogLines));
    }

    /**
     * If the user requests more lines than have been logged, but under
     * the maximum, it should still work like normal
     */
    @Test
    public void test_findMessage_EdgeCase_MoreLinesThanExists() {
        ArrayBlockingQueue<String> recentLogLines = new ArrayBlockingQueue<>(20);
        recentLogLines.offer("word1");
        recentLogLines.offer("word2");
        recentLogLines.offer("word3");
        List<String> foundWords = TestLogger.findMessage("word2", MAX_CACHE_SIZE - 1, recentLogLines);
        assertEquals(foundWords.getFirst(), "word2");
    }

    /**
     * There is a program, "checkValidity", that ensures that "findFirstMessage"
     * is returning just one message.
     */
    @Test
    public void test_findFirstMessage_CheckValidity_NoLogs() {
        List<String> recentLogLines = List.of();
        assertThrows(TestLoggerException.class,
                "foo was not found in \n\t",
                () -> TestLogger.checkValidityOfResults("foo", List.of(), recentLogLines));
    }

    @Test
    public void test_findFirstMessage_CheckValidity_NotFound() {
        List<String> recentLogLines = List.of("word1", "word2", "word3");
        var ex = assertThrows(TestLoggerException.class,
                () -> TestLogger.checkValidityOfResults("foo", List.of(), recentLogLines));
        assertEquals(ex.getMessage(), "foo was not found in \n\tword1\n" +
                "\tword2\n" +
                "\tword3");
    }

    @Test
    public void test_findFirstMessage_CheckValidity_TooMany() {
        List<String> recentLogLines = List.of("word1", "word2", "word2", "word3");
        assertThrows(TestLoggerException.class,
                "multiple values of word2 found in: [word1, word2, word2, word3]",
                () -> TestLogger.checkValidityOfResults("word2", List.of("word2","word2"), recentLogLines));
    }

    /**
     * If we don't find the log message we expected, an
     * exception should be thrown.
     */
    @Test
    public void test_doesMessageExist_NegativeCase() {
        var ex = assertThrows(TestLoggerException.class, () -> logger.doesMessageExist("foo foo"));
        assertTrue(ex.getMessage().contains("foo foo was not found in"));
    }

    /**
     * A simple test for testLoggerQueue, just to get a handle on it.
     */
    @Test
    public void test_testLoggerQueue_Basic() {
        var tlq = new TestLoggerQueue(1);
        assertTrue(tlq.add("abc"));
        assertTrue(tlq.add("123"));
        assertFalse(tlq.contains("abc"));
    }
}
