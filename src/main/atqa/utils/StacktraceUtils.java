package atqa.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Collectors;

public class StacktraceUtils {
    /**
     * grabs the stacktrace out of a {@link Throwable} as a string
     */
    public static String stackTraceToString(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    public static String stackTraceToString(StackTraceElement[] elements) {
        return Arrays.stream(elements).map(StackTraceElement::toString).collect(Collectors.joining(";"));
    }

}
