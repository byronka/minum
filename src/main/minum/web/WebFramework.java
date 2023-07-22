package minum.web;

import minum.Constants;
import minum.Context;
import minum.FullSystem;
import minum.database.Db;
import minum.database.DbData;
import minum.logging.ILogger;
import minum.security.TheBrig;
import minum.security.UnderInvestigation;
import minum.testing.StopwatchUtils;
import minum.utils.StringUtils;
import minum.utils.ThrowingConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static minum.web.StatusLine.StatusCode._404_NOT_FOUND;
import static minum.web.WebEngine.HTTP_CRLF;

/**
 * Responsible for the logistics after socket connection
 * <p>
 * Relies on server sockets built in {@link WebEngine}.  Builds
 * a function in {@link #makePrimaryHttpHandler()} that sort-of fits into a
 * slot in WebEngine and handles HTTP protocol.  Also handles
 * routing and static files.
 */
public class WebFramework {

    private final Constants constants;
    private final UnderInvestigation underInvestigation;
    private final InputStreamUtils inputStreamUtils;
    private final StopwatchUtils stopWatchUtils;
    private final BodyProcessor bodyProcessor;

    /**
     * This is used as a key when registering endpoints
     */
    record VerbPath(StartLine.Verb verb, String path) { }

    /**
     * The list of paths that our system is registered to handle.
     */
    private final Map<VerbPath, Function<Request, Response>> registeredDynamicPaths;

    /**
     * These are registrations for paths that partially match, for example,
     * if the client sends us GET /.well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX
     * and we want to match
     */
    private final Map<VerbPath, Function<Request, Response>> registeredPartialPaths;

    // This is just used for testing.  If it's null, we use the real time.
    private final ZonedDateTime overrideForDateTime;
    private final FullSystem fs;
    private StaticFilesCache staticFilesCache;
    private final ILogger logger;
    private final Context context;

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
    ThrowingConsumer<ISocketWrapper, IOException> makePrimaryHttpHandler(Function<StartLine, Function<Request, Response>> handlerFinder) {

        // build the handler

        return (sw) -> {
            try (sw) {

                // if we recognize this client as an attacker, dump them.
                TheBrig theBrig = (fs != null && fs.getTheBrig() != null) ? fs.getTheBrig() : null;
                if (theBrig != null && constants.IS_THE_BRIG_ENABLED) {
                    String remoteClient = sw.getRemoteAddr();
                    if (theBrig.isInJail(remoteClient + "_vuln_seeking")) {
                        // if this client is a vulnerability seeker, just dump them unceremoniously
                        logger.logDebug(() -> "closing the socket on " + remoteClient);
                        sw.close();
                        return;
                    }
                }

                var fullStopwatch = stopWatchUtils.startTimer();
                final var is = sw.getInputStream();

                /*
                By default, browsers expect the server to run in keep-alive mode.
                We'll break out later if we find that the browser doesn't do keep-alive
                 */
                while(true) {

                    // first grab the start line (see https://developer.mozilla.org/en-US/docs/Web/HTTP/Messages#start_line)
                    // e.g. GET /foo HTTP/1.1
                    final var rawStartLine = inputStreamUtils.readLine(is);
                     /*
                      if the rawStartLine is null, that means the client stopped talking.
                      See ISocketWrapper.readLine()
                      */
                    if (rawStartLine == null) {
                        // no need to do any further work, just bail
                        return;
                    }

                    logger.logTrace(() -> sw + ": raw startline received: " + rawStartLine);
                    var sl = StartLine.EMPTY(context).extractStartLine(rawStartLine);
                    logger.logTrace(() -> sw + ": StartLine received: " + sl.toString());
                    if (sl.getRawValue().isBlank()) {
                        /*
                        if we get in here, it means the client sent nothing in the spot
                        where the Start Line should have been - therefore, we're not
                        dealing with a kosher request at all. Bail.
                         */
                        return;
                    }

                    Function<Request, Response> endpoint = handlerFinder.apply(sl);

                    // The response we will send to the client
                    Response resultingResponse;
                    boolean isKeepAlive = false;

                    /*
                       next we will read the headers (e.g. Content-Type: foo/bar) one-by-one.

                       by the way, the headers will tell us vital information about the
                       body.  If, for example, we're getting a POST and receiving a
                       www form url encoded, there will be a header of "content-length"
                       that will mention how many bytes to read.  On the other hand, if
                       we're receiving a multipart, there will be no content-length, but
                       the content-type will include the boundary string.
                    */
                    var headers = Headers.make(context, inputStreamUtils);
                    Headers hi = headers.extractHeaderInformation(sw.getInputStream());
                    logger.logTrace(() -> "The headers are: " + hi.getHeaderStrings());

                    // determine if we are in a keep-alive connection
                    if (sl.getVersion() == WebEngine.HttpVersion.ONE_DOT_ZERO) {
                        isKeepAlive = hi.hasKeepAlive();
                    } else if (sl.getVersion() == WebEngine.HttpVersion.ONE_DOT_ONE) {
                        isKeepAlive = ! hi.hasConnectionClose();
                    }
                    boolean finalIsKeepAlive = isKeepAlive;
                    logger.logTrace(() -> "Is this a keep-alive connection? " + finalIsKeepAlive);

                    Body body = Body.EMPTY(context);
                    // Determine whether there is a body (a block of data) in this request
                    if (isThereIsABody(hi)) {
                        logger.logTrace(() -> "There is a body. Content-type is " + hi.contentType());
                        body = bodyProcessor.extractData(sw.getInputStream(), hi);
                        Body finalBody = body;
                        logger.logTrace(() -> "The body is: " + finalBody.asString());
                    }

                    /*
                    Need to postpone processing until here, even though the request may be a 404 not found,
                    because this way we can review the headers and other aspects of the body and maybe
                    keep the keep-alive socket open.  For example, if the user is having an entire
                    conversation with us and asks for a favicon and we're missing it, we shouldn't
                    abruptly close the socket for merely that.

                    Now, on the other hand, if they are looking for vulnerabilities, it's ok to dump them.
                     */
                    if (endpoint == null) {
                        logger.logDebug(() -> String.format("%s requested an unregistered path of %s.  Returning 404", sw, sl.getPathDetails().isolatedPath()));
                        boolean isVulnSeeking = underInvestigation.isLookingForSuspiciousPaths(sl.getPathDetails().isolatedPath());
                        logger.logDebug(() -> "Is " + sw.getRemoteAddr() + " looking for a vulnerability? " + isVulnSeeking);
                        if (isVulnSeeking && theBrig != null && constants.IS_THE_BRIG_ENABLED) {
                            theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", constants.VULN_SEEKING_JAIL_DURATION);
                            return;
                        }
                        resultingResponse = new Response(_404_NOT_FOUND);
                    } else {
                        var handlerStopwatch = new StopwatchUtils().startTimer();
                        resultingResponse = endpoint.apply(new Request(hi, sl, body, sw.getRemoteAddr()));
                        logger.logTrace(() -> String.format("handler processing of %s %s took %d millis", sw, sl, handlerStopwatch.stopTimer()));
                    }

                    String statusLineAndHeaders = convertResponseToString(resultingResponse, isKeepAlive);

                    // Here is where the bytes actually go out on the socket
                    String response = statusLineAndHeaders + HTTP_CRLF;
                    logger.logTrace(() -> "Sending back: " + response + StringUtils.byteArrayToString(resultingResponse.body()));
                    sw.send(response);
                    sw.send(resultingResponse.body());
                    logger.logTrace(() -> String.format("full processing (including communication time) of %s %s took %d millis", sw, sl, fullStopwatch.stopTimer()));

                    if (! isKeepAlive) break;
                }
            }
        };
    }

    /**
     * Determine whether the headers in this HTTP message indicate that
     * a body is available to read
     */
    public boolean isThereIsABody(Headers hi) {
        return !hi.contentType().isBlank() &&
                (hi.contentLength() > 0 || hi.valueByKey("transfer-encoding").stream().anyMatch(x -> x.equalsIgnoreCase("chunked")));
    }

    /**
     * This is where our strongly-typed {@link Response} gets converted
     * to a string and sent on the socket.
     */
    private String convertResponseToString(Response r, boolean isKeepAlive) {
        String date = Objects.requireNonNullElseGet(overrideForDateTime, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        StringBuilder stringBuilder = new StringBuilder();

        // add the status line
        stringBuilder.append( "HTTP/1.1 " + r.statusCode().code + " " + r.statusCode().shortDescription + HTTP_CRLF );

        // add the headers
        stringBuilder
                .append( "Date: " + date + HTTP_CRLF )
                .append( "Server: minum" + HTTP_CRLF )
                .append(  r.extraHeaders().stream().map(x -> x + HTTP_CRLF).collect(Collectors.joining()))
                .append("Content-Length: " + r.body().length + HTTP_CRLF );

        // if we're a keep-alive connection, reply with a keep-alive header

        if (isKeepAlive) {
            stringBuilder
                    .append("Keep-Alive: timeout=" + constants.SOCKET_TIMEOUT_MILLIS / 1000 + HTTP_CRLF);
        }

        return stringBuilder.toString();
    }

    /**
     * This is the brains of how the server responds to web clients. Whatever
     * code lives here will be inserted into a slot within the server code
     * This builds a handler {@link java.util.function.Consumer} that provides
     * the code to be run in the web testing engine that selects which
     * function to run for a particular HTTP request.  See {@link #makePrimaryHttpHandler(Function)}
     */
    public ThrowingConsumer<ISocketWrapper, IOException> makePrimaryHttpHandler() {
        return makePrimaryHttpHandler(this::findEndpointForThisStartline);
    }

    /**
     * Looks through the mappings of {@link VerbPath} and path to registered endpoints
     * or the static cache and returns the appropriate one (If we
     * do not find anything, return null)
     */
    Function<Request, Response> findEndpointForThisStartline(StartLine sl) {
        Function<Request, Response> handler;

        // first we check if there's a simple direct match
        String requestedPath = sl.getPathDetails().isolatedPath().toLowerCase(Locale.ROOT);
        handler = registeredDynamicPaths.get(new VerbPath(sl.getVerb(), requestedPath));

        if (handler == null) {
            // if there's no direct match, let's see if we can match the
            // registered paths against part of the startline
            logger.logTrace(() -> "No direct handler found.  looking for a partial match for " + requestedPath);
            var verbPathFunctionEntry = registeredPartialPaths.entrySet().stream()
                    .filter(x -> requestedPath.startsWith(x.getKey().path()) &&
                            x.getKey().verb().equals(sl.getVerb()))
                    .findFirst().orElse(null);
            if (verbPathFunctionEntry != null) {
                handler = verbPathFunctionEntry.getValue();
            }
        }

        if (handler == null) {
            // if nothing was found in the registered dynamic endpoints, look
            // through the static cache of responses
            logger.logTrace(() -> "Did not find a function to handle a verb of " + sl.getVerb() + " and a path of " + requestedPath);
            final Response staticResponseFound = staticFilesCache.getStaticResponse(requestedPath);
            if (staticResponseFound != null) {
                logger.logTrace(() -> "found a static value to handle "+requestedPath+", returning it");
                handler = request -> staticResponseFound;
            }
        }

        if (handler == null) {
            logger.logTrace(() -> "Found neither a function nor a static value in the cache, checking files on disk");
            // last ditch effort - look on disk.  This response will either
            // be the file to return, or null if we didn't find anything.
            Response response = staticFilesCache.loadStaticFile(requestedPath);
            if (response != null) {
                handler = request -> response;
            }
        }

        // we'll return this, and it could be a null.
        return handler;
    }

    /**
     * This constructor is used for the real production system
     */
    public WebFramework(Context context) {
        this(context, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param overrideForDateTime for those test cases where we need to control the time
     */
    public WebFramework(Context context, ZonedDateTime overrideForDateTime) {
        this.fs = context.getFullSystem();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.overrideForDateTime = overrideForDateTime;
        this.registeredDynamicPaths = new HashMap<>();
        this.registeredPartialPaths = new HashMap<>();
        this.staticFilesCache = new StaticFilesCache(logger);
        this.context = context;
        this.underInvestigation = new UnderInvestigation(constants);
        this.inputStreamUtils = new InputStreamUtils(context);
        this.stopWatchUtils = new StopwatchUtils();
        this.bodyProcessor = new BodyProcessor(context);
    }

    /**
     * Add a new handler in the web application for a combination
     * of a {@link minum.web.StartLine.Verb}, a path, and then provide
     * the code to handle a request.
     * <br>
     * Note that the path text expected is *after* the first forward slash,
     * so for example with {@code http://foo.com/mypath}, you provide us "mypath"
     * here.
     */
    public void registerPath(StartLine.Verb verb, String pathName, Function<Request, Response> webHandler) {
        registeredDynamicPaths.put(new VerbPath(verb, pathName), webHandler);
    }


    public void registerPartialPath(StartLine.Verb verb, String pathName, Function<Request, Response> webHandler) {
        registeredPartialPaths.put(new VerbPath(verb, pathName), webHandler);
    }

    public void registerStaticFiles(StaticFilesCache sfc) {
        this.staticFilesCache = sfc;
    }

    /**
     * Since this is a generic method, a bit of care is required when
     * calling.  Try to use a pattern like this:
     * <pre>
     * {@code Db<Photograph> photoDb = wf.getDb("photos");}
     * </pre>
     */
    public <T extends DbData<?>> Db<T> getDb(String name, T instance) {
        return new Db<>(Path.of(constants.DB_DIRECTORY, name), context, instance);
    }
}
