package atqa.instrumentation;

import atqa.logging.TestLogger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class InstrumentationTests {

    private final TestLogger logger;

    public InstrumentationTests(TestLogger logger) {
        this.logger = logger;
    }

    class Program1 {
        int hello;
    }
    class Program2 {
        int world;
    }

    public void tests(ExecutorService es) throws IOException, ClassNotFoundException {
        logger.test("playing with viewing bytecode");{
            final var resource = this.getClass();
            final Class<?> aClass = this.getClass().getClassLoader().loadClass("atqa.instrumentation.InstrumentationTests");
            System.out.println(resource);
        }

        logger.test("seeing the diff between two mostly-similar java programs"); {
//            final bytes[] program1 = //get bytes of Program1
//            final bytes[] program2 = //get bytes of Program2
//            assertTrue(program1 != program2);
        }
    }
}
