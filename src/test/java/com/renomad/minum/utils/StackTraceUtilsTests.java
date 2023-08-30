package com.renomad.minum.utils;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class StackTraceUtilsTests {
    @Test
    public void test_StackTraceToString() {
        String result = StacktraceUtils.stackTraceToString(new RuntimeException("foo"));
        assertTrue(result.contains("java.lang.RuntimeException: foo"));
    }

    @Test
    public void test_StackTraceElementsToString() {
        String result = StacktraceUtils.stackTraceToString(new RuntimeException("foo").getStackTrace());
        assertTrue(result.contains("minum.utils.StackTraceUtilsTests.tests(StackTraceUtilsTests.java:25);"));
    }
}
