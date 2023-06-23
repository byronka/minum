package minum.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {

    public static String getTimestampIsoInstant() {
        return ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
    }

    public static String getLocalDateStamp() {
        return LocalDate.now(ZoneId.of("US/Eastern")).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
