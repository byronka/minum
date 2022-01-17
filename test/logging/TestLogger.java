package logging;

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
        loggerPrinter.enqueue(() -> printf("%n+-------------%n| TEST %d: %s%n+-------------%n%n", testCount++, msg));
    }

}
