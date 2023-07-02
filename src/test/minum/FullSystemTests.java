package minum;

import minum.testing.TestLogger;
import minum.web.FakeSocketWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertTrue;

public class FullSystemTests {
    private final TestLogger logger;
    private final ExecutorService es;
    private final TestContext context;

    public FullSystemTests(TestContext context) {
        this.logger = context.getLogger();
        this.es = context.getExecutorService();
        this.context = context;
        logger.testSuite("FullSystem Tests", "FullSystemTests");
    }

    /*
    When we run the redirection handler, it will redirect all traffic
    on the socket to the HTTPS endpoint.
     */
    public void tests() throws IOException {

        /*
         * Sometimes a client will connect to TCP but then close their
         * connection, in which case when we readline it will return as null,
         * and we'll return early from the handler, returning nothing.
         */
        logger.test("Typical happy path - a user makes an HTTP request to the insecure endpoint"); {
            FullSystem fullSystem = new FullSystem(es, context.getConstants());
            var redirectHandler = fullSystem.makeRedirectHandler();
            FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
            fakeSocketWrapper.bais = new ByteArrayInputStream("The startline\n".getBytes(StandardCharsets.UTF_8));
            redirectHandler.accept(fakeSocketWrapper);
            String result = fakeSocketWrapper.baos.toString();
            assertTrue(result.contains("303 SEE OTHER"), "result was: " + result);
        }

        /*
         * Sometimes a client will connect to TCP but then close their
         * connection, in which case when we readline it will return as null,
         * and we'll return early from the handler, returning nothing.
         */
        logger.test("If the redirect handler receives no start line, return nothing"); {
            FullSystem fullSystem = new FullSystem(es, context.getConstants());
            var redirectHandler = fullSystem.makeRedirectHandler();
            FakeSocketWrapper fakeSocketWrapper = new FakeSocketWrapper();
            redirectHandler.accept(fakeSocketWrapper);
            assertEquals(fakeSocketWrapper.baos.toString(), "");
        }

    }
}
