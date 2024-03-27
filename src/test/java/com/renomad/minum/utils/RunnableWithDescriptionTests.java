package com.renomad.minum.utils;

import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class RunnableWithDescriptionTests {

    @Test
    public void testRunnableWithDescription() {
        var rwd = new RunnableWithDescription(() -> System.out.println("hello runnable"), "testing a runnable with description");
        assertEquals(rwd.toString(), "testing a runnable with description");
    }
}
