package minum.security;

import minum.testing.TestLogger;
import minum.utils.MyThread;

import java.util.concurrent.ExecutorService;

import static minum.testing.TestFramework.assertFalse;
import static minum.testing.TestFramework.assertTrue;

public class TheBrigTests {

    private final TestLogger logger;

    public TheBrigTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {

        /*
        A user should be able to put a particular address in jail for
        a time and after it has paid its dues, be released.
         */
        logger.test("Put in jail for a time"); {
            var b = new TheBrig(10, es, logger, false).initialize();
            b.sendToJail("1.2.3.4_too_freq_downloads", 20);
            assertTrue(b.isInJail("1.2.3.4_too_freq_downloads"));
            MyThread.sleep(70);
            assertFalse(b.isInJail("1.2.3.4_too_freq_downloads"));
            b.stop();
        }
    }
}
