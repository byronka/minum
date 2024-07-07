package com.renomad.minum.utils;

import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.queue.ActionQueueKiller;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * There's not much to test here, it's very state-driven
 */
public class ActionQueueKillerTests {

    private Context context;
    private TestLogger logger;

    @Before
    public void init() {
        context = buildTestingContext("ActionQueueKillerTests");
        logger = (TestLogger) context.getLogger();
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void test_KillAllQueues_WithDebug() {
        var actionQueue = new ActionQueue("testing the killAllQueues method", context);
        var aqk = new ActionQueueKiller(context);
        context.getActionQueueState().offerToQueue(actionQueue);

        aqk.killAllQueues();
        MyThread.sleep(40);
        assertTrue(logger.doesMessageExist("Stopping queue testing the killAllQueues method"));
    }

    @Test
    public void test_KillAllQueues_WithoutDebug() {
        var actionQueue = new ActionQueue("testing the killAllQueues method", context);
        var aqk = new ActionQueueKiller(context);
        context.getActionQueueState().offerToQueue(actionQueue);

        aqk.killAllQueues();
        MyThread.sleep(40);
        assertTrue(logger.doesMessageExist("Stopping queue testing the killAllQueues method"));
    }

    @Test
    public void test_KillAllQueues() {
        TestLogger killAllQueuesLogger = new TestLogger(context.getConstants(), context.getExecutorService(), "testing kill all queues");
        Context context1 = new Context(context.getExecutorService(), context.getConstants());
        context1.setLogger(killAllQueuesLogger);
        var aqk = new ActionQueueKiller(context1);

        aqk.killAllQueues();

        assertTrue(context1.getActionQueueState().isAqQueueEmpty());
    }

    /**
     * It will sometimes be necessary to interrupt
     * the inner thread of an action queue if it
     * is not done in time (see ActionQueue.stop()).
     * In that case, we will send an interrupt event
     * which will cause the thread to be "interrupted"
     * and more forcibly halted.
     * <br>
     * This tests that.
     */
    @Test
    public void test_KillAllQueues_NeedingInterruption() {
        ActionQueue aq = new ActionQueue("testing interruption", context).initialize();
        context.getActionQueueState().offerToQueue(aq);
        var aqk = new ActionQueueKiller(context);
        Thread.ofVirtual().start(() -> {
                aq.enqueue("testing interruption", () -> {
                    MyThread.sleep(40);
                    System.out.println("hello");
                });
        });
        assertFalse(aqk.hadToInterrupt());

        aqk.killAllQueues();

        assertTrue(context.getActionQueueState().isAqQueueEmpty());
        assertTrue(aqk.hadToInterrupt());
    }

}
