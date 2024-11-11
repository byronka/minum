package com.renomad.minum.logging;

import com.renomad.minum.state.Constants;
import com.renomad.minum.utils.MyThread;

import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This implementation of {@link Logger} has a few
 * extra functions that only apply to tests, like {@link #test(String)}
 */
public final class TestLogger extends Logger {

    private final Queue<String> recentLogLines;
    public static final int MAX_CACHE_SIZE = 30;
    private final ReentrantLock loggingLock;
    private int testCount = 0;

    /**
     * See {@link TestLogger}
     */
    public TestLogger(Constants constants, ExecutorService executorService, String name) {
        super(constants, executorService, name);
        this.recentLogLines = new TestLoggerQueue(MAX_CACHE_SIZE);
        this.loggingLock = new ReentrantLock();
    }

    /**
     * A helper to get the string message value out of a
     * {@link ThrowingSupplier}
     */
    private String extractMessage(ThrowingSupplier<String, Exception> msg) {
        String receivedMessage;
        try {
            receivedMessage = msg.get();
        } catch (Exception ex) {
            receivedMessage = "EXCEPTION DURING GET: " + ex;
        }
        return receivedMessage;
    }

    /**
     * Keeps a record of the recently-added log messages, which is
     * useful for some tests.
     */
    private void addToCache(ThrowingSupplier<String, Exception> msg) {
        // put log messages into the tail of the queue
        String message = extractMessage(msg);
        String safeMessage = message == null ? "(null message)" : message;
        recentLogLines.add(safeMessage);
    }

    @Override
    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        loggingLock.lock();
        try {
            addToCache(msg);
            super.logDebug(msg);
        } finally {
            loggingLock.unlock();
        }
    }

    @Override
    public void logTrace(ThrowingSupplier<String, Exception> msg) {
        loggingLock.lock();
        try {
            addToCache(msg);
            super.logTrace(msg);
        } finally {
            loggingLock.unlock();
        }
    }

    @Override
    public void logAudit(ThrowingSupplier<String, Exception> msg) {
        loggingLock.lock();
        try {
            addToCache(msg);
            super.logAudit(msg);
        } finally {
            loggingLock.unlock();
        }
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        loggingLock.lock();
        try {
            addToCache(msg);
            super.logAsyncError(msg);
        } finally {
            loggingLock.unlock();
        }
    }

    /**
     * Provides an ability to search over the recent past log messages,
     * case-insensitively.
     * @param lines number of lines of log messages to look back through,
     *              up to {@link #MAX_CACHE_SIZE}
     */
    public String findFirstMessageThatContains(String value, int lines) {
        List<String> values = findMessage(value, lines, recentLogLines);
        List<String> logsBeingSearched = logLinesToSearch(lines, recentLogLines);
        return checkValidityOfResults(value, values, logsBeingSearched);
    }

    /**
     * This is used in {@link #findFirstMessageThatContains(String, int)} to
     * handle exceptional situations with the results.  Specifically, exceptions
     * are:
     * <ol>
     *    <li>If there were no results found</li>
     *    <li>If there were multiple results found</li>
     * </ol>
     */
    static String checkValidityOfResults(String value, List<String> values, List<String> recentLogLines) {
        int size = values.size();
        if (size == 0) {
            throw new TestLoggerException(value + " was not found in \n\t" + String.join("\n\t", recentLogLines));
        } else if (size >= 2) {
            throw new TestLoggerException("multiple values of "+value+" found in: " + recentLogLines);
        } else {
            return values.getFirst();
        }
    }

    /**
     * Whether the given string exists in the log messages. May
     * exist multiple times.
     * @param value a string to search in the log
     * @param lines how many lines back to examine
     * @return whether this string was found, even if there
     *      were multiple places it was found.
     */
    public boolean doesMessageExist(String value, int lines) {
        if (! findMessage(value, lines, recentLogLines).isEmpty()) {
            return true;
        } else {
            List<String> logsBeingSearched = logLinesToSearch(lines, recentLogLines);
            throw new TestLoggerException(value + " was not found in \n\t" + String.join("\n\t", logsBeingSearched));
        }
    }

    /**
     * Whether the given string exists in the log messages. May
     * exist multiple times.
     * @param value a string to search in the log
     * @return whether or not this string was found, even if there
     * were multiple places it was found.
     */
    public boolean doesMessageExist(String value) {
        return doesMessageExist(value, 3);
    }

    static List<String> findMessage(String value, int lines, Queue<String> recentLogLines) {
        if (lines > MAX_CACHE_SIZE) {
            throw new TestLoggerException(String.format("Can only get up to %s lines from the log", MAX_CACHE_SIZE));
        }
        if (lines <= 0) {
            throw new TestLoggerException("number of recent log lines must be a positive number");
        }
        MyThread.sleep(20);
        var lineList = logLinesToSearch(lines, recentLogLines);
        return lineList.stream().filter(x -> x.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))).toList();

    }

    private static List<String> logLinesToSearch(int lines, Queue<String> recentLogLines) {
        var fromIndex = Math.max(recentLogLines.size() - lines, 0);
        return recentLogLines.stream().toList().subList(fromIndex, recentLogLines.size());
    }

    /**
     * Looks back through the last 3 log messages for one that
     * contains the provided value.  Returns the whole line if
     * found and an exception if not found.
     * <p>
     *     See {@link #findFirstMessageThatContains(String, int)} if you
     *     want to search through more than 3.  However, it is only
     *     possible to search up to {@link #MAX_CACHE_SIZE}
     * </p>
     */
    public String findFirstMessageThatContains(String value) {
        return findFirstMessageThatContains(value, 3);
    }

    /**
     * A helper function to log a test title prefixed with "TEST:"
     * <br>
     * Also collects data about the previously-run test
     */
    public void test(String msg) {
        // put together some pretty-looking text graphics to show the suiteName of our test in log
        loggingLock.lock();
        try {
            final var baseLength = 11;
            final var dashes = "-".repeat(msg.length() + baseLength);

            loggingActionQueue.enqueue("Testlogger#test("+msg+")", () -> {
                testCount += 1;
                System.out.printf("%n+%s+%n| TEST %d: %s |%n+%s+%n%n", dashes, testCount, msg, dashes);
                recentLogLines.add(msg);
            });
        } finally {
            loggingLock.unlock();
        }
    }

    public int getTestCount() {
        return testCount;
    }

    @Override
    public String toString() {
        return "TestLogger using queue: " + super.loggingActionQueue.toString();
    }

}
