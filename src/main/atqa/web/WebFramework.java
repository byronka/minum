package atqa.web;

import atqa.TheRegister;
import atqa.logging.ILogger;
import atqa.utils.InvariantException;
import atqa.utils.StringUtils;
import atqa.utils.ThrowingConsumer;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import static atqa.web.InputStreamUtils.*;
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
     *
     * @param handlerFinder bear with me...  This is a function, that takes a {@link StartLine}, and
     *                      returns a {@link Function} that handles the {@link Request} -> {@link Response}.
     *                      Normally, you would just use {@link #makeHandler()} and the default code at
     *                      {@link #findHandlerForEndpoint(StartLine)} would be called.  However, you can provide
     *                      a handler here if you want to override that behavior, for example in tests when
     *                      you want a bit more control.
     */
    public ThrowingConsumer<SocketWrapper, IOException> makeHandler(Function<StartLine, Function<Request, Response>> handlerFinder) {
        return (sw) -> {
            try (sw) {
                final var is = sw.getInputStream();
                // first grab the start line (see https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line)
                // e.g. GET /foo HTTP/1.1
                final var rawStartLine = readLine(is);
                 /*
                  if the rawStartLine is null, that means the client stopped talking.
                  See ISocketWrapper.readLine()
                  */
                if (rawStartLine == null) {
                    return;
                }

                logger.logTrace(() -> sw + ": raw startline received: " + rawStartLine);
                StartLine sl = StartLine.extractStartLine(rawStartLine);
                logger.logTrace(() -> sw + ": StartLine received: " + sl);

                /*
                   next we will read the headers (e.g. Content-Type: foo/bar) one-by-one.

                   by the way, the headers will tell us vital information about the
                   body.  If, for example, we're getting a POST and receiving a
                   www form url encoded, there will be a header of "content-length"
                   that will mention how many bytes to read.  On the other hand, if
                   we're receiving a multipart, there will be no content-length, but
                   the content-type will include the boundary string.
                */
                Headers hi = Headers.extractHeaderInformation(sw.getInputStream());

                Map<String, byte[]> bodyMap = new HashMap<>();

                // Determine whether there is a body (a block of data) in this request
                final var thereIsABody = ! hi.contentType().isBlank();
                if (thereIsABody) {
                    bodyMap = extractData(sw.getInputStream(), hi);
                }

                Function<Request, Response> endpoint = handlerFinder.apply(sl);
                Response r = endpoint.apply(new Request(hi, sl, bodyMap));

                String date = Objects.requireNonNullElseGet(zdt, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

                sw.send(
                        "HTTP/1.1 " + r.statusCode().code + " " + r.statusCode().shortDescription + HTTP_CRLF +
                                "Date: " + date + HTTP_CRLF +
                                "Server: atqa" + HTTP_CRLF +
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
     * This is the brains of how the server responds to web clients. Whatever
     * code lives here will be inserted into a slot within the server code
     * This builds a handler {@link java.util.function.Consumer} that provides
     * the code to be run in the web framework engine that selects which
     * function to run for a particular HTTP request.  See {@link #makeHandler(Function)}
     */
    public ThrowingConsumer<SocketWrapper, IOException> makeHandler() {
        return makeHandler(this::findHandlerForEndpoint);
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
                        List.of("Content-Type: text/html; charset=UTF-8"),
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
    public static Map<String, byte[]> parseUrlEncodedForm(String input) {
        if (input.isEmpty()) return Collections.emptyMap();

        final var postedPairs = new HashMap<String, byte[]>();
        final var splitByAmpersand = StringUtils.tokenizer(input, '&');

        for(final var s : splitByAmpersand) {
            final var pair = splitKeyAndValue(s);
            mustBeTrue(pair.length == 2, "Splitting on = should return 2 values.  Input was " + s);
            mustBeTrue(! pair[0].isBlank(), "The key must not be blank");
            final var result = postedPairs.put(pair[0], decode(pair[1]).getBytes(StandardCharsets.UTF_8));
            if (result != null) {
                throw new InvariantException(pair[0] + " was duplicated in the post body - had values of "+StringUtils.byteArrayToString(result)+" and " + pair[1]);
            }
        }
        return postedPairs;
    }

    public static Map<String, String> convertStringByteMap(Map<String, byte[]> myMap) {
        Map<String, String> result = new HashMap<>();
        for (var key : myMap.keySet()) {
            result.put(key, StringUtils.byteArrayToString(myMap.get(key)));
        }
        return result;
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
        return new String[] {
                formInputValue.substring(0, locationOfEqual),
                formInputValue.substring(locationOfEqual+1)};
    }

    /**
     * read the body if one exists
     * <br>
     * There are really only two ways to read the body.
     * 1. the client tells us how many bytes to read
     * 2. the client uses "transfer-encoding: chunked"
     * <br>
     * In either case, it is absolutely critical that the client gives us
     * a way to know ahead of time how many bytes to read, so we (the server)
     * can stop reading at precisely the right point.  There's simply no
     * other way to reasonably do this.
     */
    private Map<String, byte[]> extractData(InputStream is, Headers h) throws IOException {
        final var contentType = h.contentType();

        byte[] bodyBytes = h.contentLength() > 0 ?
                InputStreamUtils.read(h.contentLength(), is) :
                readChunkedEncoding(is);

        if (h.contentLength() > 0 && contentType.contains("application/x-www-form-urlencoded")) {
            return parseUrlEncodedForm(StringUtils.byteArrayToString(bodyBytes));
        } else if (contentType.contains("multipart/form-data")) {
            String boundaryKey = "boundary=";
            int indexOfBoundaryKey = contentType.indexOf(boundaryKey);
            if (indexOfBoundaryKey > 0) {
                // grab all the text after the key
                String boundaryValue = contentType.substring(indexOfBoundaryKey + boundaryKey.length());
                return parseMultiform(bodyBytes, boundaryValue);
            }
            logger.logDebug(() -> "Did not find a valid boundary value for the multipart input.  Returning an empty map for the body");
            return Collections.emptyMap();
        } else {
            logger.logDebug(() -> "Did not find a recognized content-type, returning an empty map for the body");
            return Collections.emptyMap();
        }
    }

    public static Map<String, byte[]> parseMultiform(byte[] body, String boundaryValue) throws IOException {
        // how to split this up? It's a mix of strings and bytes.
        String[] splitString = StringUtils.byteArrayToString(body).split("--"+boundaryValue+".*\n");
        final List<String> dataForms = Arrays.stream(splitString).filter(x -> ! x.isBlank()).toList();
        final String nameEquals = "name=";
        // What we can bear in mind is that once we've read the headers, and gotten
        // past the single blank line, *everything else* is pure data.
        final var result = new HashMap<String, byte[]>();
        for (var df : dataForms) {
            final var is = new ByteArrayInputStream(df.getBytes(StandardCharsets.UTF_8));
            Headers headers = Headers.extractHeaderInformation(is);
            String contentDisposition = headers.headerStrings().stream()
                    .filter(x -> x.toLowerCase().contains("form-data") && x.contains(nameEquals)).collect(Collectors.joining());
            int i = contentDisposition.indexOf(nameEquals) + nameEquals.length();
            String textWithQuotes = contentDisposition.substring(i);
            String name = textWithQuotes.substring(1, textWithQuotes.length() - 1);
            // at this point our inputstream pointer is at the beginning of the
            // body data.  From here until the end it's pure data.
            byte[] data = readUntilEOF(is);
            result.put(name, data);
        }
        return result;
    }

    public void registerStaticFiles(StaticFilesCache sfc) {
        this.staticFilesCache = sfc;
    }
}
