package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.Invariants.*;

public class InvariantsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("InvariantsTests");
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
    public void test_MustBeTrue() {
        mustBeTrue(true, "testing...");
        var ex = assertThrows(InvariantException.class, () -> mustBeTrue(false, "testing..."));
        assertEquals(ex.getMessage(), "testing...");
    }

    @Test
    public void test_MustBeFalse() {
        mustBeFalse(false, "testing...");
        var ex = assertThrows(InvariantException.class, () -> mustBeFalse(true, "testing..."));
        assertEquals(ex.getMessage(), "testing...");
    }

    @Test
    public void test_MustNotBeNull() {
        Map<Object, Object> mapObject = Map.of();
        var object = mustNotBeNull(mapObject);
        assertTrue(object == object);
        var ex = assertThrows(InvariantException.class, () -> mustNotBeNull(null));
        assertEquals(ex.getMessage(), "value must not be null");
    }
}
