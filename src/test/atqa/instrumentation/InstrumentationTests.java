package atqa.instrumentation;

import atqa.logging.TestLogger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class InstrumentationTests {

    private final TestLogger logger;

    public InstrumentationTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) throws IOException {
        logger.test("playing with viewing bytecode");{
            final var resource = this.getClass().getResource("InstrumentationTests.class");
        }
    }
}
