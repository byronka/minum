package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static com.renomad.minum.testing.TestFramework.*;

public class StackTraceUtilsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("StackTraceUtilsTests");
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
    public void test_StackTraceToString() {
        String result = StacktraceUtils.stackTraceToString(new RuntimeException("foo"));
        assertTrue(result.contains("java.lang.RuntimeException: foo"));
    }

    @Test
    public void test_StackTraceElementsToString() {
        String result = StacktraceUtils.stackTraceToString(new RuntimeException("foo").getStackTrace());
        assertTrue(result.contains("com.renomad.minum.utils.StackTraceUtilsTests.test_StackTraceElementsToString(StackTraceUtilsTests.java"));
    }
}
