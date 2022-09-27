package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.ThrowingConsumer;

import java.io.IOException;
import java.net.SocketException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static atqa.utils.Invariants.mustBeTrue;
import static atqa.utils.StringUtils.decode;
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
                String body = extractData(sw, hi);

                Function<Request, Response> endpoint = findHandlerForEndpoint(sl);
                Response r = endpoint.apply(new Request(hi, sl));

                String date = getZonedDateTimeNow(zdt).format(DateTimeFormatter.RFC_1123_DATE_TIME);

                sw.sendHttpLine(
                        "HTTP/1.1 " + r.statusCode().code + " " + r.statusCode().shortDescription + HTTP_CRLF +
                                "Date: " + date + HTTP_CRLF +
                                "Server: atqa" + HTTP_CRLF +
                                r.contentType().headerString + HTTP_CRLF +
                                r.extraHeaders().stream().map(x -> x + HTTP_CRLF).collect(Collectors.joining()) +
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

    public record Request(HeaderInformation hi, StartLine sl, String body) {
        public Request(HeaderInformation hi, StartLine sl) {
            this(hi, sl, "");
        }
    }

    public record Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders, String body) {
        public Response(StatusLine.StatusCode statusCode, ContentType contentType, String body) {
            this(statusCode, contentType, Collections.emptyList(), body);
        }
        public Response(StatusLine.StatusCode statusCode, ContentType contentType, List<String> extraHeaders) {
            this(statusCode, contentType, extraHeaders, "");
        }
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

    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> webHandler) {
        endpoints.put(new VerbPath(verb, pathName), webHandler);
    }


    /**
     * Parse data formatted by application/x-www-form-urlencoded
     * See https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST
     *
     * See here for the encoding: https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding
     *
     * for example, valuea=3&valueb=this+is+something
     */
    public static Map<String, String> parseUrlEncodedForm(String input) {
        if (input.isEmpty()) return Collections.emptyMap();

        final var postedPairs = new HashMap<String, String>();
        final var splitByAmpersand = input.split("&");

        for(final var s : splitByAmpersand) {
            final var pair = s.split("=");
            mustBeTrue(pair.length == 2, "Splitting on = should return 2 values.  Input was " + s);
            mustBeTrue(! pair[0].isBlank(), "The key must not be blank");
            final var result = postedPairs.put(pair[0], decode(pair[1]));
            if (result != null) {
                throw new RuntimeException(pair[0] + " was duplicated in the post body - had values of "+result+" and " + pair[1]);
            }
        }
        return postedPairs;
    }

    /**
     * read the body if one exists
     */
    private String extractData(Web.ISocketWrapper server, HeaderInformation hi) throws IOException {
        if (hi.contentLength() > 0) {
            return server.read(hi.contentLength());
        } else {
            return "";
        }
    }
}
