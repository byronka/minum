package com.renomad.minum.utils;

import com.renomad.minum.testing.StopwatchUtils;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertFalse;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class MyThreadTests {

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

        assertFalse(sleptWithoutInterruption);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    public void test_InterruptionHandler() {
        MyThread.handleInterrupted(new InterruptedException());
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
