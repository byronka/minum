package com.renomad.minum.logging;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.FileUtils;

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
    private final List<TestSuite> testSuites;
    private String previousTestName;
    private TestSuite currentTestSuite;
    private Queue<String> recentLogLines;
    private final int MAX_CACHE_SIZE = 20;
    private final ReentrantLock logDebugLock;
    private final ReentrantLock logAuditLock;
    private final ReentrantLock logTraceLock;
    private final ReentrantLock logAsyncErrorLock;
    private int testCount = 1;

    /**
     * Writes a Junit-style xml file to out/reports/tests/YOUR_FILENAME_HERE.xml
     * @param filename the name of the test report.  Since this is
     *                 an XML file, it will receive a suffix of .xml
     */
    public void writeTestReport(String filename) throws IOException {
        if (currentTestSuite != null) {
            includeTimingForPreviousTest();
        }
        StringBuilder sb = new StringBuilder();
        long totalTime = 0;
        long totalCountTests = 0;
        for (TestSuite ts : testSuites) {
            List<String> testCaseStrings = ts.testCases().stream().map(x -> String.format("\t\t<testcase classname=\"%s\" name=\"%s\" time=\"%.3f\" />", ts.className(), x.name, x.duration() / 1000.0)).toList();
            long sumOfTestCaseDurations = ts.testCases.stream().mapToLong(x -> x.duration).sum();
            totalTime += sumOfTestCaseDurations;
            totalCountTests += testCaseStrings.size();
            String testSuiteString = String.format("\t<testsuite name=\"%s\" time=\"%.2f\" tests=\"%d\">", ts.className(), sumOfTestCaseDurations / 1000.0, testCaseStrings.size());
            sb.append(String.format("%n" + testSuiteString + "%n" + String.join("\n", testCaseStrings) + "%n" + "\t</testsuite>" + "%n"));
        }
        String innerXmlReport = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites time="%.2f" tests="%d">
                """.formatted(totalTime / 1000.0, totalCountTests) + sb + """
                </testsuites>
                """;
        Files.createDirectories(Path.of("out/reports/tests"));
        writeString(Path.of("out/reports/tests/"+filename+".xml"), innerXmlReport);
    }

    /**
     * An organizational tool - indicates in the test report that the following
     * tests are within this testing suite
     * @param className copy the name of the testing class for this parameter
     */
    public void testSuite(String className) {
        // if there is an existing test suite, we need to add the timing for the
        // last test here in that previous suite.
        if (currentTestSuite != null) {
            includeTimingForPreviousTest();
        }
        currentTestSuite = new TestSuite(className, new ArrayList<>());
        testSuites.add(currentTestSuite);
    }

    /**
     * Represents a particular test case
     */
    record TestCase(String name, long duration) {}

    /**
     * Represents a suite of tests, typically the set
     * of tests in a particular class
     */
    record TestSuite(String className, List<TestCase> testCases) {}

    /**
     * See {@link TestLogger}
     */
    public TestLogger(Constants constants, ExecutorService executorService, String name) {
        super(constants, executorService, name);
        this.testSuites = new ArrayList<>();
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
        var lineList = recentLogLines.stream().skip(MAX_CACHE_SIZE-lines).toList();
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
        currentTestSuite.testCases().add(new TestCase(msg, 0));

        includeTimingForPreviousTest();
        previousTestName = msg;
        // put together some pretty-looking text graphics to show the suiteName of our test in log
        final var baseLength = 11;
        final var dashes = "-".repeat(msg.length() + baseLength);

        loggingActionQueue.enqueue("Testlogger#test("+msg+")", () -> {
            Object[] args = new Object[]{testCount++, msg};
            System.out.printf("%n+"  + dashes + "+%n| TEST %d: %s |%n+"+ dashes + "+%n%n", args);
        });
    }

    /**
     * Code for calculating how long the previous test took, replaces
     * the data from that previous test with new data that includes the timing.
     */
    private void includeTimingForPreviousTest() {
        // enter information for previous test
        if (stopWatch != null && previousTestName != null) {
             // The time taken for the previous test to complete.
            long previousTestMillis = stopWatch.stopTimer();
            TestCase testCase = currentTestSuite.testCases().stream()
                    .filter(x -> x.name().equals(previousTestName))
                    .findFirst()
                    .orElse(null);
            if (testCase != null) {
                var testCaseWithTiming = new TestCase(testCase.name(), previousTestMillis);
                currentTestSuite.testCases().remove(testCase);
                currentTestSuite.testCases.add(testCaseWithTiming);
            }
        }

        // reset for next test.
        stopWatch = stopWatch == null ? new StopwatchUtils() : stopWatch.startTimer();
    }

}
