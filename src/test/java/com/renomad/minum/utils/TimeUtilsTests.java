package com.renomad.minum.utils;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.renomad.minum.testing.TestFramework.assertEquals;

public class TimeUtilsTests {

    @Test
    public void test_HappyPath() {
        ZonedDateTime now = ZonedDateTime.of(1978, 1, 4, 9, 17, 00, 00, ZoneId.of("UTC"));
        String result = TimeUtils.getTimestampIsoInstantInner(now);
        assertEquals(result, "1978-01-04T09:17:00Z");
    }
}
