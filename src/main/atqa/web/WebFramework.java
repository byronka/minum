package atqa.web;

import atqa.logging.ILogger;
import atqa.utils.StopwatchUtils;
import atqa.utils.ThrowingConsumer;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static atqa.web.InputStreamUtils.*;
import static atqa.web.StatusLine.StatusCode._404_NOT_FOUND;
import static atqa.web.WebEngine.HTTP_CRLF;

/**
 * Responsible for the logistics after socket connection
 * <p>
 * Relies on server sockets built in {@link WebEngine}.  Builds
 * a function in {@link #makePrimaryHttpHandler()} that sort-of fits into a
 * slot in WebEngine and handles HTTP protocol.  Also handles
 * routing and static files.
 */
public class WebFramework {

    /**
     * The logger to be used in the system.  Stored here to avoid
     * some boilerplate parameters during registration of request -> response handlers.
     */
    public final ILogger logger;

    /**
     * The list of paths that our system is registered to handle.
     */
    private final Map<VerbPath, Function<Request, Response>> registeredDynamicPaths;

    // This is just used for testing.  If it's null, we use the real time.
    private final ZonedDateTime overrideForDateTime;

    /**
     * The primary {@link ExecutorService} for the whole dang system
     */
    public final ExecutorService executorService;

    /**
     * The root directory of the database
     */
    public final Path dbDir;

    private StaticFilesCache staticFilesCache;

    /**
     * Returns a 303 response pointing to the web location you indicate
     * @param location a string form of a valid web location
     */
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
     *                      Normally, you would just use {@link #makePrimaryHttpHandler()} and the default code at
     *                      {@link #findEndpointForThisStartline(StartLine)} would be called.  However, you can provide
     *                      a handler here if you want to override that behavior, for example in tests when
     *                      you want a bit more control.
     *                      <br>
     *                      The common case definition of this is found at {@link #findEndpointForThisStartline}
     */
    public ThrowingConsumer<SocketWrapper, IOException> makePrimaryHttpHandler(Function<StartLine, Function<Request, Response>> handlerFinder) {

        // build the handler
        ThrowingConsumer<SocketWrapper, IOException> primaryHttpHandler = (sw) -> {
            try (sw) {
                var fullStopwatch = new StopwatchUtils().startTimer();
                final var is = sw.getInputStream();
                // first grab the start line (see https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line)
                // e.g. GET /foo HTTP/1.1
                final var rawStartLine = readLine(is);
                 /*
                  if the rawStartLine is null, that means the client stopped talking.
                  See ISocketWrapper.readLine()
                  */
                if (rawStartLine == null) {
                    // no need to do any further work, just bail
                    return;
                }

                logger.logTrace(() -> sw + ": raw startline received: " + rawStartLine);
                StartLine sl = StartLine.extractStartLine(rawStartLine);
                logger.logTrace(() -> sw + ": StartLine received: " + sl);

                /*
                At this point we have a start line.  That's enough to see whether a 404
                would be an appropriate response.
                 */
                Function<Request, Response> endpoint = handlerFinder.apply(sl);

                // The response we will send to the client
                Response resultingResponse;

                if (endpoint == null) {
                    logger.logDebug(() -> String.format("%s requested an unregistered path of %s.  Returning 404", sw, sl.pathDetails().isolatedPath()));
                    resultingResponse = new Response(_404_NOT_FOUND);
                } else {
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

                    var bp = new BodyProcessor(logger);
                    Body body = Body.EMPTY;
                    // Determine whether there is a body (a block of data) in this request
                    final var thereIsABody = !hi.contentType().isBlank();
                    if (thereIsABody) {
                        logger.logTrace(() -> "There is a body. Content-type is " + hi.contentType());
                        body = bp.extractData(sw.getInputStream(), hi);
                    }

                    var handlerStopwatch = new StopwatchUtils().startTimer();
                    resultingResponse = endpoint.apply(new Request(hi, sl, body, sw.getRemoteAddr()));
                    logger.logTrace(() -> String.format("handler processing of %s %s took %d millis", sw, sl, handlerStopwatch.stopTimer()));
                }

                String responseString = convertResponseToString(resultingResponse);

                // Here is where the bytes actually go out on the socket
                sw.send(responseString);
                sw.send(resultingResponse.body());
                logger.logTrace(() -> String.format("full processing (including communication time) of %s %s took %d millis", sw, sl, fullStopwatch.stopTimer()));

            } catch (SocketException | SocketTimeoutException ex) {
                /*
                 if we close the application on the server side, there's a good
                 likelihood a SocketException will come bubbling through here.
                 NOTE:
                   it seems that Socket closed is what we get when the client closes the connection in non-SSL, and conversely,
                   if we are operating in secure (i.e. SSL/TLS) mode, we get "an established connection..."
                 */
                logger.logDebug(() -> ex.getMessage() + " - remote address: " + sw.getRemoteAddrWithPort());
            } catch (SSLException ex) {
                logger.logDebug(() -> ex.getMessage() + " (at WebFramework)");
            }
        };

        return primaryHttpHandler;
    }

    /**
     * This is where our strongly-typed {@link Response} gets converted
     * to a string and sent on the socket.
     */
    private String convertResponseToString(Response r) {
        String date = Objects.requireNonNullElseGet(overrideForDateTime, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        return "HTTP/1.1 " + r.statusCode().code + " " + r.statusCode().shortDescription + HTTP_CRLF +
            "Date: " + date + HTTP_CRLF +
            "Server: atqa" + HTTP_CRLF +
            r.extraHeaders().stream().map(x -> x + HTTP_CRLF).collect(Collectors.joining()) +
            "Content-Length: " + r.body().length + HTTP_CRLF +
            HTTP_CRLF;
    }

    /**
     * This is the brains of how the server responds to web clients. Whatever
     * code lives here will be inserted into a slot within the server code
     * This builds a handler {@link java.util.function.Consumer} that provides
     * the code to be run in the web testing engine that selects which
     * function to run for a particular HTTP request.  See {@link #makePrimaryHttpHandler(Function)}
     */
    public ThrowingConsumer<SocketWrapper, IOException> makePrimaryHttpHandler() {
        return makePrimaryHttpHandler(this::findEndpointForThisStartline);
    }

    /**
     * Looks through the mappings of {@link VerbPath} and path to registered endpoints
     * or the static cache and returns the appropriate one (If we
     * do not find anything, return null)
     */
    private Function<Request, Response> findEndpointForThisStartline(StartLine sl) {
        final var functionFound = registeredDynamicPaths.get(new VerbPath(sl.verb(), sl.pathDetails().isolatedPath().toLowerCase(Locale.ROOT)));
        if (functionFound == null) {

            // if nothing was found in the registered dynamic endpoints, look
            // through the static endpoints
            final var staticResponseFound = staticFilesCache.getStaticResponse(sl.pathDetails().isolatedPath().toLowerCase(Locale.ROOT));

            if (staticResponseFound != null) {
                return request -> staticResponseFound;
            } else {
                return null;
            }
        }
        return functionFound;
    }

    /**
     * This constructor is used for the real production system
     */
    public WebFramework(ExecutorService es, ILogger logger, Path dbDir) {
        this(es, logger, null, dbDir);
    }

    /**
     * This constructor is more commonly used during testing.  Note the
     * parameter available for overriding the current date and time, and
     * no ability to set the database directory!
     */
    public WebFramework(ExecutorService es, ILogger logger, ZonedDateTime overrideForDateTime) {
        this(es, logger, overrideForDateTime, Path.of("out/simple_db"));
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param es is the {@link ExecutorService} that will be provided to downstream users. We are
     *           just requesting it here so we can dole it out to domains during registry
     * @param logger the {@link ILogger} we will use for logging throughout the system
     * @param overrideForDateTime for those test cases where we need to control the time
     * @param dbDir The root directory of the database
     */
    public WebFramework(ExecutorService es, ILogger logger, ZonedDateTime overrideForDateTime, Path dbDir) {
        this.executorService = es;
        this.logger = logger;
        this.overrideForDateTime = overrideForDateTime;
        this.dbDir = dbDir;
        this.registeredDynamicPaths = new HashMap<>();
        this.staticFilesCache = new StaticFilesCache(logger);
    }

    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> webHandler) {
        registeredDynamicPaths.put(new VerbPath(verb, pathName), webHandler);
    }


    public void registerStaticFiles(StaticFilesCache sfc) {
        this.staticFilesCache = sfc;
    }
}
