package com.renomad.minum.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Helper functions for manipulating stack traces.
 */
public final class StacktraceUtils {

    private StacktraceUtils() {
        // cannot construct
    }

    /**
     * grabs the stacktrace out of a {@link Throwable} as a string
     */
    public static String stackTraceToString(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Converts an array of {@link StackTraceElement} to a single string, joining
     * them with a semicolon as delimiter. This way our stacktrace becomes a single line.
     */
    public static String stackTraceToString(StackTraceElement[] elements) {
        return Arrays.stream(elements).map(StackTraceElement::toString).collect(Collectors.joining(";"));
    }

}
