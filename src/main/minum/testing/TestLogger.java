package minum.testing;

import minum.logging.Logger;
import minum.utils.ExtendedExecutor;
import minum.utils.FileUtils;
import minum.utils.TimeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * This implementation of {@link Logger} has a few
 * extra functions that only apply to tests, like {@link #test(String)}
 * <br><br>
 * There's also {@link TestRecordingLogger}, which is used to record
 * whatever gets logged, so we can examine it as part of testing.
 */
public class TestLogger extends Logger {

    private final ExecutorService es;
    private StopWatch stopWatch;
    private List<TestSuite> testSuites;
    private String innerXmlReport = "";

    /**
     * The time taken for the previous test to complete.
     */
    private long previousTestMillis;

    private String previousTestName;

    private TestSuite currentTestSuite;

    /**
     * Writes a Junit-style xml file to out/reports/tests/tests.xml
     */
    public void writeTestReport() throws IOException {
        StringBuilder sb = new StringBuilder();
        long totalTime = 0;
        long totalCountTests = 0;
        for (TestSuite ts : testSuites) {
            List<String> testCaseStrings = ts.testCases().stream().map(x -> String.format("\t\t<testcase name=\"%s\" time=\"%.3f\" />", x.name, x.duration() / 1000.0)).toList();
            long sumOfTestCaseDurations = ts.testCases.stream().mapToLong(x -> x.duration).sum();
            totalTime += sumOfTestCaseDurations;
            totalCountTests += testCaseStrings.size();
            String testSuiteString = String.format("\t<testsuite name=\"%s\" time=\"%.2f\" tests=\"%d\">", ts.name, sumOfTestCaseDurations / 1000.0, testCaseStrings.size());
            sb.append(String.format("%n" + testSuiteString + "%n" + String.join("\n", testCaseStrings) + "%n" + "\t</testsuite>" + "%n"));
        }
        innerXmlReport = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites time="%.2f" tests="%d">
                """.formatted(totalTime / 1000.0, totalCountTests) + sb + """
                </testsuites>
                """;
        Files.createDirectories(Path.of("out/reports/tests"));
        FileUtils.writeString("out/reports/tests/tests.xml", innerXmlReport);
    }

    public void testSuite(String msg) {
        currentTestSuite = new TestSuite(msg, new ArrayList<>());
        testSuites.add(currentTestSuite);
    }

    record TestCase(String name, long duration) {}
    record TestSuite(String name, List<TestCase> testCases) {}

    public TestLogger(ExecutorService es) {
        super(es);
        this.es = es;
        this.testSuites = new ArrayList<>();
    }

    public static TestLogger makeTestLogger() {
        var es = ExtendedExecutor.makeExecutorService();
        return new TestLogger(es);
    }

    private int testCount = 1;

    /**
     * A little helper function to log a test title prefixed with "TEST:"
     * <br>
     * Also collects data about the previously-run test
     */
    public void test(String msg) {
        // enter information for previous test
        if (stopWatch != null && previousTestName != null) {
            previousTestMillis = stopWatch.stopTimer();
            currentTestSuite.testCases().add(new TestCase(previousTestName, previousTestMillis));
        }

        // reset for next test.
        stopWatch = new StopWatch();
        previousTestName = msg;

        // put together some pretty-looking text graphics to show the name of our test in log
        final var baseLength = 11;
        final var dashes = "-".repeat(msg.length() + baseLength);

        loggerPrinter.enqueue("Testlogger#test("+msg+")", () -> {
            printf("%n+"  + dashes + "+%n| TEST %d: %s |%n+"+ dashes + "+%n%n", testCount++, msg);
            return null;
        });
    }

    /**
     * Used for printing pertinent messages during the tests, including
     * exceptions thrown.
     */
    public void testPrint(String msg) {
        loggerPrinter.enqueue("Testlogger#testPrint("+msg+")", () -> {
            System.out.println(msg);
            return null;
        });
    }

    /**
     * Appends the message provided to the end of the file indicated by path.
     * Prepends a time/date stamp.
     */
    public void testPrintToFile(String msg, Path path) {
        loggerPrinter.enqueue("Testlogger#testPrintToFile("+msg+","+path+")", () -> {
            Files.writeString(path, TimeUtils.getLocalDateStamp() + "\t" + msg + "\n", StandardOpenOption.APPEND);
            return null;
        });
    }

    public ExecutorService getExecutorService() {
        return es;
    }
}
