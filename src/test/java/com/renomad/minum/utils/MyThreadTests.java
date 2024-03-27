package com.renomad.minum.utils;

import com.renomad.minum.testing.StopwatchUtils;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertTrue;

public class MyThreadTests {

    @Test
    public void testMyThread() {
        var stopwatchUtils = new StopwatchUtils().startTimer();
        MyThread.sleep(10);
        assertTrue(stopwatchUtils.stopTimer() >= 10, "Sleep time should be at least 10 milliseconds");
    }

    @Test
    public void testMyThread_Interrupted() {
        Thread currentThread = Thread.currentThread();
        var interruptingThread = new Thread(() -> {
            MyThread.sleep(5);
            currentThread.interrupt();
        });
        var stopwatchUtils = new StopwatchUtils().startTimer();
        interruptingThread.run();
        MyThread.sleep(30);
        long duration = stopwatchUtils.stopTimer();
        assertTrue(duration < 30,
                "The interruption should have caused this to take less than 30 millis.  Actual time: " + duration);
    }

    @Test
    public void test_InterruptionHandler() {
        MyThread.handleInterrupted(new InterruptedException());
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
