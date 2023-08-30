package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertTrue;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;

public class ThrowingRunnableTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        logger = (TestLogger) context.getLogger();
    }

    /**
     * Trying to handle exceptions well in a multi-threaded system is a matter
     * of compromises.  The purpose here is to at least have the opportunity to
     * log the exception.
     */
    @Test
    public void testThrowingRunnable() {
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
