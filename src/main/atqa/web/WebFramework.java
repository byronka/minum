package atqa.web;

import atqa.database.DatabaseDiskPersistenceSimpler;
import atqa.database.SimpleDataType;
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
public class WebFramework implements AutoCloseable {

    /**
     * The list of paths that our system is registered to handle.
     */
    private final Map<VerbPath, Function<Request, Response>> registeredDynamicPaths;

    // This is just used for testing.  If it's null, we use the real time.
    private final ZonedDateTime overrideForDateTime;
    private final FullSystem fs;
    private StaticFilesCache staticFilesCache;
    private final ILogger logger;

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
    ThrowingConsumer<SocketWrapper, IOException> makePrimaryHttpHandler(Function<StartLine, Function<Request, Response>> handlerFinder) {

        // build the handler

        return (sw) -> {
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
    ThrowingConsumer<SocketWrapper, IOException> makePrimaryHttpHandler() {
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

    public WebFramework(ILogger logger, ZonedDateTime default_zdt) {
        this(null, logger, default_zdt);
    }

    /**
     * This constructor is used for the real production system
     */
    public WebFramework(FullSystem fs) {
        this(fs, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param overrideForDateTime for those test cases where we need to control the time
     */
    public WebFramework(FullSystem fs, ZonedDateTime overrideForDateTime) {
        this(fs, fs.logger, overrideForDateTime);
    }

    private WebFramework(FullSystem fs, ILogger logger, ZonedDateTime overrideForDateTime) {
        this.fs = fs;
        this.logger = logger;
        this.overrideForDateTime = overrideForDateTime;
        this.registeredDynamicPaths = new HashMap<>();
        this.staticFilesCache = new StaticFilesCache(logger);
    }

    /**
     * Add a new handler in the web application for a combination
     * of a {@link atqa.web.StartLine.Verb}, a path, and then provide
     * the code to handle a request.
     * <br>
     * Note that the path text expected is *after* the first forward slash,
     * so for example with {@code http://foo.com/mypath}, you provide us "mypath"
     * here.
     */
    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> webHandler) {
        registeredDynamicPaths.put(new VerbPath(verb, pathName), webHandler);
    }


    void registerStaticFiles(StaticFilesCache sfc) {
        this.staticFilesCache = sfc;
    }

    /**
     * Since this is a generic method, a bit of care is required when
     * calling.  Try to use a pattern like this:
     * <pre>
     * {@code DatabaseDiskPersistenceSimpler<Photograph> photoDdps = wf.getDdps("photos");}
     * </pre>
     */
    public <T extends SimpleDataType<?>> DatabaseDiskPersistenceSimpler<T> getDdps(String name) {
        var dbDir = Path.of(FullSystem.getConfiguredProperties().getProperty("dbdir", "out/simple_db/"));
        return new DatabaseDiskPersistenceSimpler<>(dbDir.resolve(name), fs.es, fs.logger);
    }

    public ILogger getLogger() {
        return logger;
    }

    public FullSystem getFullSystem() {
        return this.fs;
    }

    @Override
    public void close() throws IOException {
        if (fs != null) this.fs.close();
    }
}
