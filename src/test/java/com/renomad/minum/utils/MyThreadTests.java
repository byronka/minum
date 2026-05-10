package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;

public class MyThreadTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("MythreadTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    @Test
    public void testMyThread() {
        var stopwatchUtils = new StopwatchUtils().startTimer();
        boolean sleptWithoutInterruption = MyThread.sleep(10);
        assertTrue(stopwatchUtils.stopTimer() >= 10, "Sleep time should be at least 10 milliseconds");
        assertTrue(sleptWithoutInterruption);
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void testMyThread_Interrupted() {
        Thread currentThread = Thread.currentThread();
        var interruptingThread = new Thread(() -> {
            MyThread.sleep(5);
            currentThread.interrupt();
        });
        interruptingThread.start();

        boolean sleptWithoutInterruption = MyThread.sleep(30);

        assertFalse(sleptWithoutInterruption, "MyThread should have been interrupted");
        assertTrue(Thread.currentThread().isInterrupted(), "MyThread should have been interrupted");
    }

    @Test
    public void test_InterruptionHandler() {
        MyThread.handleInterrupted(new InterruptedException());
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
