package com.renomad.minum.utils;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertTrue;

public class TimeUtilsTests {

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
