package atqa.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

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
}
