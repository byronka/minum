package com.renomad.minum.logging;

import com.renomad.minum.Constants;
import com.renomad.minum.testing.StopwatchUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.Files.writeString;

/**
 * This implementation of {@link Logger} has a few
 * extra functions that only apply to tests, like {@link #test(String)}
 */
public class TestLogger extends Logger {

    private StopwatchUtils stopWatch;
    private String previousTestName;
    private Queue<String> recentLogLines;
    private final int MAX_CACHE_SIZE = 20;
    private final ReentrantLock logDebugLock;
    private final ReentrantLock logAuditLock;
    private final ReentrantLock logTraceLock;
    private final ReentrantLock logAsyncErrorLock;
    private int testCount = 1;

    /**
     * See {@link TestLogger}
     */
    public TestLogger(Constants constants, ExecutorService executorService, String name) {
        super(constants, executorService, name);
        this.stopWatch = new StopwatchUtils();
        this.recentLogLines = new ArrayBlockingQueue<>(MAX_CACHE_SIZE);
        this.logDebugLock = new ReentrantLock();
        this.logTraceLock = new ReentrantLock();
        this.logAuditLock = new ReentrantLock();
        this.logAsyncErrorLock = new ReentrantLock();
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
        while (recentLogLines.size() >= (MAX_CACHE_SIZE)) {
            // pull log messages off the head of the queue
            recentLogLines.remove();
        }
        // put log messages into the tail of the queue
        String message = extractMessage(msg);
        String safeMessage = message == null ? "" : message;
        recentLogLines.offer(safeMessage);
    }

    @Override
    public void logDebug(ThrowingSupplier<String, Exception> msg) {
        logDebugLock.lock();
        try {
            addToCache(msg);
            super.logDebug(msg);
        } finally {
            logDebugLock.unlock();
        }
    }

    @Override
    public void logTrace(ThrowingSupplier<String, Exception> msg) {
        logTraceLock.lock();
        try {
            addToCache(msg);
            super.logTrace(msg);
        } finally {
            logTraceLock.unlock();
        }
    }

    @Override
    public void logAudit(ThrowingSupplier<String, Exception> msg) {
        logAuditLock.lock();
        try {
            addToCache(msg);
            super.logAudit(msg);
        } finally {
            logAuditLock.unlock();
        }
    }

    @Override
    public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        logAsyncErrorLock.lock();
        try {
            addToCache(msg);
            super.logAsyncError(msg);
        } finally {
            logAsyncErrorLock.unlock();
        }
    }

    /**
     * Provides an ability to search over the recent past log messages,
     * case-insensitively.
     * @param lines number of lines of log messages to look back through,
     *              up to {@link #MAX_CACHE_SIZE}
     */
    public String findFirstMessageThatContains(String value, int lines) {
        if (lines > MAX_CACHE_SIZE) throw new RuntimeException(String.format("Can only get up to %s lines from the log", MAX_CACHE_SIZE));
        var fromIndex = recentLogLines.size() - lines < 0 ? 0 : recentLogLines.size() - lines;
        var lineList = recentLogLines.stream().toList().subList(fromIndex, recentLogLines.size());
        var values = lineList.stream().filter(x -> x.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))).toList();
        int size = values.size();
        if (size == 0) {
            throw new RuntimeException(value + " was not found in " + String.join(";", lineList));
        } else if (size == 1) {
            return values.get(0);
        } else if (size >= 2) {
            throw new RuntimeException("multiple values found: " + values);
        }
        throw new RuntimeException("Shouldn't be possible to get here.");
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
        previousTestName = msg;
        // put together some pretty-looking text graphics to show the suiteName of our test in log
        final var baseLength = 11;
        final var dashes = "-".repeat(msg.length() + baseLength);

        loggingActionQueue.enqueue("Testlogger#test("+msg+")", () -> {
            Object[] args = new Object[]{testCount++, msg};
            System.out.printf("%n+"  + dashes + "+%n| TEST %d: %s |%n+"+ dashes + "+%n%n", args);
        });
    }

}
