package com.renomad.minum.utils;

import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.testing.RegexUtils;
import org.junit.AfterClass;
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

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
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

    @Test
    public void test_Stopping() {
        var aq = new ActionQueue("Test ActionQueue", context).initialize();
        assertFalse(aq.isStopped());

        aq.enqueue("testing action", () -> {
            MyThread.sleep(10);
            System.out.println("a test message");
        });

        aq.stop(0,0);
        var msg = logger.findFirstMessageThatContains("Queue Test ActionQueue has");
        assertFalse(RegexUtils.find("Queue Test ActionQueue has .? elements left but we're done waiting", msg).isEmpty());
        assertTrue(aq.isStopped());
        assertThrows(UtilsException.class,
                "failed to enqueue check if stopped - ActionQueue \"Test ActionQueue\" is stopped",
                () ->  aq.enqueue("check if stopped", () -> System.out.println("testing if stopped")));
        assertEquals(aq.getQueue().size(), 0);
    }
}
