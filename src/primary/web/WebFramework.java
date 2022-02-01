package primary.web;

import logging.ILogger;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Responsible for the logistics after socket connection
 *
 * After we've made a valid socket connection with a client, there's a
 * lot of logistics needed. For example, routing based on the path, determining
 * proper response headers, and doing it all with panache.
 *
 */
public class WebFramework {

    /**
     * This is the brains of how the server responds to web clients
     */
    public Consumer<Web.SocketWrapper> makeHandler() {
     return (sw) -> {
         StartLine sl = StartLine.extractStartLine(sw.readLine());
         HeaderInformation hi = HeaderInformation.extractHeaderInformation(sw);

         int aValue = Integer.parseInt(sl.getQueryString().get("a"));
         int bValue = Integer.parseInt(sl.getQueryString().get("b"));
         int sum = aValue + bValue;
         String sumString = String.valueOf(sum);

         sw.sendHttpLine("HTTP/1.1 200 OK");
         String date = getZonedDateTimeNow().format(DateTimeFormatter.RFC_1123_DATE_TIME);
         sw.sendHttpLine("Date: " + date);
         sw.sendHttpLine("Server: atqa");
         sw.sendHttpLine("Content-Type: text/plain; charset=UTF-8");
         sw.sendHttpLine("Content-Length: " + sumString.length());
         sw.sendHttpLine("");
         sw.sendHttpLine(sumString);
     };
    }

    private final ILogger logger;

    public WebFramework(ILogger logger) {
        this(logger, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param logger
     * @param zdt
     */
    public WebFramework(ILogger logger, ZonedDateTime zdt) {
        this.logger = logger;
        this.zdt = zdt;
    }

    private final ZonedDateTime zdt;

    private ZonedDateTime getZonedDateTimeNow() {
        if (zdt != null) {
            return zdt;
        } else {
            return ZonedDateTime.now(ZoneId.of("UTC"));
        }
    }
}
