package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

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
        Queue<AbstractActionQueue> listOfQueues = new LinkedBlockingQueue<>();
        listOfQueues.add(actionQueue);

        aqk.killAllQueues(listOfQueues);
        MyThread.sleep(40);
        assertTrue(logger.doesMessageExist("Stopping queue testing the killAllQueues method"));
    }

    @Test
    public void test_KillAllQueues_WithoutDebug() {
        var actionQueue = new ActionQueue("testing the killAllQueues method", context);
        var aqk = new ActionQueueKiller(context);
        Queue<AbstractActionQueue> listOfQueues = new LinkedBlockingQueue<>();
        listOfQueues.add(actionQueue);

        aqk.killAllQueues(listOfQueues);
        MyThread.sleep(40);
        assertTrue(logger.doesMessageExist("Stopping queue testing the killAllQueues method"));
    }

    @Test
    public void test_KillAllQueues() {
        Context context1 = new Context();
        TestLogger killAllQueuesLogger = new TestLogger(context.getConstants(), context.getExecutorService(), "testing kill all queues");
        context1.setLogger(killAllQueuesLogger);
        var aqk = new ActionQueueKiller(context1);

        aqk.killAllQueues();

        assertTrue(context1.getAqQueue().isEmpty());
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
        context.getAqQueue().add(aq);
        var aqk = new ActionQueueKiller(context);
        Thread.ofVirtual().start(() -> {
                aq.enqueue("testing interruption", () -> {
                    MyThread.sleep(10);
                    System.out.println("hello");
                });
        });
        assertFalse(aqk.hadToInterrupt());

        aqk.killAllQueues();

        assertTrue(context.getAqQueue().isEmpty());
        assertTrue(aqk.hadToInterrupt());
    }

}
