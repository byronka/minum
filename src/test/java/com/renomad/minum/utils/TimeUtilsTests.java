package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.testing.TestFramework.shutdownTestingContext;

public class TimeUtilsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("TimeUtilsTests");
        logger = (TestLogger) context.getLogger();
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
    public void test_HappyPath() {
        ZonedDateTime now = ZonedDateTime.of(1978, 1, 4, 9, 17, 0, 0, ZoneId.of("UTC"));
        String result = TimeUtils.getTimestampIsoInstantInner(now);
        assertEquals(result, "1978-01-04T09:17:00Z");
    }

    @Test
    public void test_HappyPath_2() {
        ZonedDateTime utc = ZonedDateTime.now(ZoneId.of("UTC"));
        String result = TimeUtils.getTimestampIsoInstant();
        Instant from = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertTrue(Math.abs(utc.toEpochSecond() - from.getEpochSecond()) < 1);
    }
}
