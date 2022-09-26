package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ThrowingConsumer;

import java.io.IOException;
import java.net.SocketException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static atqa.web.Web.HTTP_CRLF;

/**
 * Responsible for the logistics after socket connection
 * <p>
 * After we've made a valid socket connection with a client, there's a
 * lot of logistics needed. For example, routing based on the path, determining
 * proper response headers, and doing it all with panache.
 */
public class WebFramework {

    /**
     * This is the brains of how the server responds to web clients
     */
    public ThrowingConsumer<Web.SocketWrapper, IOException> makeHandler() {
        return (sw) -> {
            try (sw) {
                final var rawStartLine = sw.readLine();
             /*
              if the rawStartLine is null, that means the client stopped talking.
              See {@link java.io.BufferedReader#readline}
              */
                if (rawStartLine == null) {
                    return;
                }
                logger.logDebug(() -> sw + ": raw startline received: " + rawStartLine);
                StartLine sl = StartLine.extractStartLine(rawStartLine);
                logger.logDebug(() -> sw + ": StartLine received: " + sl);

                HeaderInformation hi = HeaderInformation.extractHeaderInformation(sw);

                Function<Request, Response> endpoint = findHandlerForEndpoint(sl);
                Response r = endpoint.apply(new Request(hi, sl));

                String date = getZonedDateTimeNow(zdt).format(DateTimeFormatter.RFC_1123_DATE_TIME);

                sw.sendHttpLine(
                        "HTTP/1.1 " + r.statusCode().code + " " + r.statusCode().shortDescription + HTTP_CRLF +
                                "Date: " + date + HTTP_CRLF +
                                "Server: atqa" + HTTP_CRLF +
                                r.contentType().headerString + HTTP_CRLF +
                                "Content-Length: " + r.body().length() + HTTP_CRLF +
                                HTTP_CRLF +
                                r.body()
                );
            } catch (SocketException ex) {
                if (!ex.getMessage().contains("Socket closed")) {
                    throw new RuntimeException(ex);
                }
            }
        };
    }

    /**
     * Looks through the mappings of {@link VerbPath} to endpoint handlers
     * and returns the appropriate one.  If we do not find anything, return a
     * very basic 404 NOT FOUND page
     */
    private Function<Request, Response> findHandlerForEndpoint(StartLine sl) {
        final var functionFound = endpoints.get(new VerbPath(sl.verb(), sl.pathDetails().isolatedPath()));
        if (functionFound == null) {
            return request -> new Response(
                    StatusLine.StatusCode._404_NOT_FOUND,
                    ContentType.TEXT_HTML,
                    "<p>404 not found using startline of " + sl + "</p>");
        }
        return functionFound;
    }

    private final ILogger logger;

    public record Request(HeaderInformation hi, StartLine sl) {
    }

    public record Response(StatusLine.StatusCode statusCode, ContentType contentType, String body) {
    }

    record VerbPath(StartLine.Verb verb, String path) {
    }

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

    public static ZonedDateTime getZonedDateTimeNow(ZonedDateTime zdt) {
        return Objects.requireNonNullElseGet(zdt, () -> ZonedDateTime.now(ZoneId.of("UTC")));
    }

    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> functionName) {
        endpoints.put(new VerbPath(verb, pathName), functionName);
    }
}
