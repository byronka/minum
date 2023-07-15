package minum.testing;

import minum.logging.LoggingContext;
import minum.logging.Logger;
import minum.utils.FileUtils;
import minum.utils.ThrowingSupplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import static minum.utils.Invariants.mustNotBeNull;

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
    private final int MAX_CACHE_SIZE = 10;

    /**
     * Writes a Junit-style xml file to out/reports/tests/tests.xml
     */
    public void writeTestReport() throws IOException {
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
        FileUtils.writeString("out/reports/tests/tests.xml", innerXmlReport);
    }

    public void testSuite(String className) {
        currentTestSuite = new TestSuite(className, new ArrayList<>());
        testSuites.add(currentTestSuite);
    }

    record TestCase(String name, long duration) {}
    record TestSuite(String className, List<TestCase> testCases) {}

    public TestLogger(LoggingContext context, String name) {
        super(context, name);
        this.testSuites = new ArrayList<>();
        this.stopWatch = new StopwatchUtils();
        this.recentLogLines = new ArrayBlockingQueue<>(MAX_CACHE_SIZE);
    }

    private int testCount = 1;

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
    public synchronized void logDebug(ThrowingSupplier<String, Exception> msg) {
        addToCache(msg);
        super.logDebug(msg);
    }

    @Override
    public synchronized void logTrace(ThrowingSupplier<String, Exception> msg) {
        addToCache(msg);
        super.logTrace(msg);
    }

    @Override
    public synchronized void logAudit(ThrowingSupplier<String, Exception> msg) {
        addToCache(msg);
        super.logAudit(msg);
    }

    @Override
    public synchronized void logAsyncError(ThrowingSupplier<String, Exception> msg) {
        addToCache(msg);
        super.logAsyncError(msg);
    }

    /**
     * Provides an ability to search over the recent past log messages,
     * case-insensitively.
     */
    public String findFirstMessageThatContains(String value) {
        var lineList = recentLogLines.stream().toList();
        var values = lineList.stream().filter(x -> x.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))).toList();
        int size = values.size();
        if (size == 0) {
            throw new RuntimeException(value + " was not found in " + lineList);
        } else if (size == 1) {
            return values.get(0);
        } else if (size >= 2) {
            throw new RuntimeException("multiple values found: " + values);
        }
        throw new RuntimeException("Shouldn't be possible to get here.");
    }

    /**
     * A little helper function to log a test title prefixed with "TEST:"
     * <br>
     * Also collects data about the previously-run test
     */
    public void test(String msg) {
        // enter information for previous test
        if (stopWatch != null && previousTestName != null) {
             // The time taken for the previous test to complete.
            long previousTestMillis = stopWatch.stopTimer();
            currentTestSuite.testCases().add(new TestCase(previousTestName, previousTestMillis));
        }

        // reset for next test.
        stopWatch = stopWatch == null ? new StopwatchUtils() : stopWatch.startTimer();
        previousTestName = msg;

        // put together some pretty-looking text graphics to show the suiteName of our test in log
        final var baseLength = 11;
        final var dashes = "-".repeat(msg.length() + baseLength);

        loggerPrinter.enqueue("Testlogger#test("+msg+")", () -> {
            printf("%n+"  + dashes + "+%n| TEST %d: %s |%n+"+ dashes + "+%n%n", testCount++, msg);
        });
    }

}
