package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

public class ByteUtilsTests {


    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("ByteUtilsTests");
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
    public void testConversionToArray() {
        assertEqualByteArray(new byte[]{1,2,3}, ByteUtils.byteListToArray(List.of((byte)1, (byte)2, (byte)3)));
        assertEqualByteArray(new byte[0], ByteUtils.byteListToArray(List.of()));
    }
}
