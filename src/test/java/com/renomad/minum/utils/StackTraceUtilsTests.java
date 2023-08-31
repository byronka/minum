package com.renomad.minum.utils;

import org.junit.Test;

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
        assertTrue(result.contains("com.renomad.minum.utils.StackTraceUtilsTests.test_StackTraceElementsToString(StackTraceUtilsTests.java"));
    }
}
