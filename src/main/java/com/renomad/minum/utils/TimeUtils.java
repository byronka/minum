package com.renomad.minum.utils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class TimeUtils {

    private TimeUtils() {
        // cannot construct
    }

    public static String getTimestampIsoInstant() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        return getTimestampIsoInstantInner(now);
    }

    static String getTimestampIsoInstantInner(ZonedDateTime now) {
        return now.truncatedTo(ChronoUnit.MICROS).format(DateTimeFormatter.ISO_INSTANT);
    }
}
