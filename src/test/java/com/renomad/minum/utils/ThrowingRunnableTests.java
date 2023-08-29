package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;

import static com.renomad.minum.testing.TestFramework.assertTrue;

public class ThrowingRunnableTests {

    private final Context context;
    private final TestLogger logger;

    public ThrowingRunnableTests(Context context) {
        this.context = context;
        this.logger = (TestLogger)context.getLogger();
        logger.testSuite("ThrowingRunnableTests");
    }

    public void tests() {
        /*
        Trying to handle exceptions well in a multi-threaded system is a matter
        of compromises.  The purpose here is to at least have the opportunity to
        log the exception.
         */
        logger.test("Confirm that wrapping a Runnable with throwingRunnableWrapper captures exceptions");
        String message = "testing throwingRunnableWrapper";
        Runnable runnable = ThrowingRunnable.throwingRunnableWrapper(() -> {
            throw new RuntimeException(message);
        }, logger);

        context.getExecutorService().submit(runnable);

        MyThread.sleep(50);
        String foundLog = logger.findFirstMessageThatContains(message, 6);
        assertTrue(foundLog.contains(message));
    }
}
