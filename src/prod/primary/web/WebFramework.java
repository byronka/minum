package primary.web;

import logging.ILogger;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
    public ThrowingConsumer<Web.SocketWrapper, IOException> makeHandler() {
     return (sw) -> {
         StartLine sl = StartLine.extractStartLine(sw.readLine());
         HeaderInformation hi = HeaderInformation.extractHeaderInformation(sw);

         Function<Request, Response> endpoint = findEndpoint(sl);
         Response r = endpoint.apply(new Request(hi, sl));

         sw.sendHttpLine("HTTP/1.1 200 OK");
         String date = getZonedDateTimeNow().format(DateTimeFormatter.RFC_1123_DATE_TIME);
         sw.sendHttpLine("Date: " + date);
         sw.sendHttpLine("Server: atqa");
         sw.sendHttpLine("Content-Type: text/plain; charset=UTF-8");
         sw.sendHttpLine("Content-Length: " + r.placeholder().length());
         sw.sendHttpLine("");
         sw.sendHttpLine(r.placeholder());
     };
    }

    private Function<Request, Response> findEndpoint(StartLine sl) {
        return endpoints.get(new VerbPath(sl.verb, sl.pathDetails.isolatedPath()));
    }

    private final ILogger logger;
    public record Request(HeaderInformation hi, StartLine sl) {}
    public record Response(String placeholder) {}
    record VerbPath(StartLine.Verb verb, String path) {}
    private final Map<VerbPath, Function<Request, Response>> endpoints;

    public WebFramework(ILogger logger) {
        this(logger, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     */
    public WebFramework(ILogger logger, ZonedDateTime zdt) {
        this.logger = logger;
        this.zdt = zdt;
        this.endpoints = new HashMap<>();
    }

    private final ZonedDateTime zdt;

    private ZonedDateTime getZonedDateTimeNow() {
        return Objects.requireNonNullElseGet(zdt, () -> ZonedDateTime.now(ZoneId.of("UTC")));
    }

    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> functionName) {
        endpoints.put(new VerbPath(verb, pathName), functionName);
    }
}
