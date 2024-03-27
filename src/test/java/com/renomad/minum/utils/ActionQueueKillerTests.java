package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
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
}
