package atqa.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;

public class TestLogger extends Logger {

    public TestLogger(ExecutorService es) {
        super(es);
    }

    private int testCount = 1;

    /**
     * A little helper function to log a test title prefixed with "TEST:"
     */
    public void test(String msg) {
        loggerPrinter.enqueue(() -> {
            printf("%n+-------------%n| TEST %d: %s%n+-------------%n%n", testCount++, msg);
            return null;
        });
    }

    /**
     * Shows that this test is skipped
     */
    public void testSkip(String msg) {
        loggerPrinter.enqueue(() -> {
            printf("%n+-------------%n|  *** SKIPPED *** TEST %d: %s%n+-------------%n%n", testCount++, msg);
            return null;
        });
    }

    public void testPrint(String msg) {
        loggerPrinter.enqueue(() -> {
            System.out.println(msg);
            return null;
        });
    }

    public static String printStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

}
