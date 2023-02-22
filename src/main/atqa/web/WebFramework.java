package atqa.web;

import atqa.TheRegister;
import atqa.logging.ILogger;
import atqa.utils.InvariantException;
import atqa.utils.StringUtils;
import atqa.utils.ThrowingConsumer;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static atqa.utils.Invariants.mustBeTrue;
import static atqa.utils.StringUtils.decode;
import static atqa.web.WebEngine.HTTP_CRLF;

/**
 * Responsible for the logistics after socket connection
 * <p>
 * Relies on server sockets built in {@link WebEngine}.  Builds
 * a function in {@link #makeHandler()} that sort-of fits into a
 * slot in WebEngine and handles HTTP protocol.  Also handles
 * routing and static files.
 */
public class WebFramework {

    public final ILogger logger;
    private final Map<VerbPath, Function<Request, Response>> registeredDynamicPaths;
    private final ZonedDateTime zdt;
    public final ExecutorService executorService;
    private StaticFilesCache staticFilesCache;

    public static Function<Request, Response> redirectTo(String location) {
        return x -> new Response(
                StatusLine.StatusCode._303_SEE_OTHER,
                List.of("location: " + location));
    }

    /**
     * This is the brains of how the server responds to web clients.  Whatever
     * code lives here will be inserted into a slot within the server code.
     * See {@link Server#start(ExecutorService, ThrowingConsumer)}
     */
    public ThrowingConsumer<SocketWrapper, IOException> makeHandler() {
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
                logger.logTrace(() -> sw + ": raw startline received: " + rawStartLine);
                StartLine sl = StartLine.extractStartLine(rawStartLine);
                logger.logTrace(() -> sw + ": StartLine received: " + sl);

                Headers hi = Headers.extractHeaderInformation(sw);
                String body = extractData(sw, hi);
                Map<String, Object> bodyMap = parseUrlEncodedForm(body);

                Function<Request, Response> endpoint = findHandlerForEndpoint(sl);
                Response r = endpoint.apply(new Request(hi, sl, body.getBytes(StandardCharsets.UTF_8), bodyMap));

                String date = Objects.requireNonNullElseGet(zdt, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

                sw.send(
                        "HTTP/1.1 " + r.statusCode().code + " " + r.statusCode().shortDescription + HTTP_CRLF +
                                "Date: " + date + HTTP_CRLF +
                                "Server: atqa" + HTTP_CRLF +
                                (r.contentType() == ContentType.NONE ? "" : r.contentType().headerString + HTTP_CRLF) +
                                r.extraHeaders().stream().map(x -> x + HTTP_CRLF).collect(Collectors.joining()) +
                                "Content-Length: " + r.body().length + HTTP_CRLF +
                                HTTP_CRLF
                );
                sw.send(r.body());
            } catch (SocketException ex) {
                // if we close the application on the server side, there's a good
                // likelihood a SocketException will come bubbling through here.
                if (!ex.getMessage().contains("Socket closed")) {
                    throw new RuntimeException(ex);
                }
            } catch (SSLException ex) {
                logger.logDebug(() -> ex.getMessage() + " at WebFramework");
            }
        };
    }

    /**
     * Looks through the mappings of {@link VerbPath} to endpoint handlers
     * and returns the appropriate one.  If we do not find anything, return a
     * very basic 404 NOT FOUND page
     */
    private Function<Request, Response> findHandlerForEndpoint(StartLine sl) {
        final var functionFound = registeredDynamicPaths.get(new VerbPath(sl.verb(), sl.pathDetails().isolatedPath().toLowerCase(Locale.ROOT)));
        if (functionFound == null) {

            // if nothing was found in the registered dynamic endpoints, look
            // through the static endpoints
            final var staticResponseFound = staticFilesCache.getStaticResponse(sl.pathDetails().isolatedPath().toLowerCase(Locale.ROOT));

            if (staticResponseFound != null) {
                return request -> staticResponseFound;
            } else {
                return request -> new Response(
                        StatusLine.StatusCode._404_NOT_FOUND,
                        ContentType.TEXT_HTML,
                        "<p>404 not found using startline of " + sl + "</p>");
            }
        }
        return functionFound;
    }

    public WebFramework(ExecutorService es, ILogger logger) {
        this(es, logger, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param es is the {@link ExecutorService} that will be provided to downstream users. We are
     *           just requesting it here so we can dole it out to domains during registry in {@link TheRegister}
     */
    public WebFramework(ExecutorService es, ILogger logger, ZonedDateTime zdt) {
        this.executorService = es;
        this.logger = logger;
        this.zdt = zdt;
        this.registeredDynamicPaths = new HashMap<>();
        this.staticFilesCache = new StaticFilesCache(logger);
    }

    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> webHandler) {
        registeredDynamicPaths.put(new VerbPath(verb, pathName), webHandler);
    }


    /**
     * Parse data formatted by application/x-www-form-urlencoded
     * See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST">...</a>
     * <p>
     * See here for the encoding: <a href="https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding">...</a>
     * <p>
     * for example, valuea=3&valueb=this+is+something
     */
    public static Map<String, Object> parseUrlEncodedForm(String input) {
        if (input.isEmpty()) return Collections.emptyMap();

        final var postedPairs = new HashMap<String, Object>();
        final var splitByAmpersand = StringUtils.tokenizer(input, '&');

        for(final var s : splitByAmpersand) {
            final var pair = splitKeyAndValue(s);
            mustBeTrue(pair.length == 2, "Splitting on = should return 2 values.  Input was " + s);
            mustBeTrue(! pair[0].isBlank(), "The key must not be blank");
            final var result = postedPairs.put(pair[0], decode(pair[1]));
            if (result != null) {
                throw new InvariantException(pair[0] + " was duplicated in the post body - had values of "+result+" and " + pair[1]);
            }
        }
        return postedPairs;
    }

    /**
     * This splits a key from its value, following the HTTP pattern
     * of "key=value". (that is, a key string, concatenated to an "equals"
     * character, concatenated to the value, with no spaces [and the key
     * and value are URL-encoded])
     * @param formInputValue a string like "key=value"
     */
    private static String[] splitKeyAndValue(String formInputValue) {
        final var locationOfEqual = formInputValue.indexOf("=");
        final var result = new String[] {
                formInputValue.substring(0, locationOfEqual),
                formInputValue.substring(locationOfEqual+1)};
        return result;
    }

    /**
     * read the body if one exists
     */
    private String extractData(ISocketWrapper server, Headers h) throws IOException {
        if (h.contentLength() > 0 && h.contentType() == ContentType.APPLICATION_FORM_URL_ENCODED) {
            return StringUtils.bytesToString(server.read(h.contentLength()));
        } else {
            return "";
        }
    }

    public void registerStaticFiles(StaticFilesCache sfc) {
        this.staticFilesCache = sfc;
    }
}
