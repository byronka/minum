package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.*;

public class ActionQueueTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger)context.getLogger();
    }

    /*
     One major concern is that actions that are handled within ActionQueue
     need to have a way to make exceptions known to the outside world. It's
     a problem with multi-threaded code - too easy to miss exceptions.
     */
    @Test
    public void test_ActionQueue_ErrorHandling() {
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
