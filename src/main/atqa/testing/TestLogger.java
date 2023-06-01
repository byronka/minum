package atqa.testing;

import atqa.logging.Logger;
import atqa.utils.ExtendedExecutor;
import atqa.utils.TimeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    public TestLogger(ExecutorService es) {
        super(es);
        this.es = es;
    }

    public static TestLogger makeTestLogger() {
        var es = ExtendedExecutor.makeExecutorService();
        return new TestLogger(es);
    }

    private int testCount = 1;

    /**
     * A little helper function to log a test title prefixed with "TEST:"
     */
    public void test(String msg) {
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
