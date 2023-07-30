package minum.utils;

import minum.Context;
import minum.testing.TestLogger;

import static minum.testing.TestFramework.assertEquals;
import static minum.testing.TestFramework.assertTrue;

public class ActionQueueTests {

    private final Context context;
    private final TestLogger logger;

    public ActionQueueTests(Context context) {
        this.context = context;
        this.logger = (TestLogger)context.getLogger();
        logger.testSuite("ActionQueueTests");
    }

    public void tests() {

        /*
         One major concern is that actions that are handled within ActionQueue
         need to have a way to make exceptions known to the outside world. It's
         a problem with multi-threaded code - too easy to miss exceptions.
         */
        logger.test("If something fails during handling, a log entry is added"); {
            String message = "This is a test of ActionQueue handling an error";
            var aq = new ActionQueue("Test ActionQueue", context).initialize();

            aq.enqueue("This should immediately fail", () -> {
                throw new RuntimeException(message);
            });

            // unavoidable race condition - if I check logger's list of messages without
            // waiting, I will definitely get there before actionqueue.
            MyThread.sleep(50);
            String loggedMessage = logger.findFirstMessageThatContains(message);
            assertTrue(!loggedMessage.isBlank(),
                    "logged message must include expected message.  What was logged: " + loggedMessage);
        }
    }
}
