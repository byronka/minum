package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.UnderInvestigation;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.web.StatusLine.StatusCode._404_NOT_FOUND;
import static com.renomad.minum.web.StatusLine.StatusCode._500_INTERNAL_SERVER_ERROR;
import static com.renomad.minum.web.WebEngine.HTTP_CRLF;

/**
 * Responsible for the logistics after socket connection
 * <p>
 * Relies on server sockets built in {@link WebEngine}.  Builds
 * a function in {@link #makePrimaryHttpHandler()} that sort-of fits into a
 * slot in WebEngine and handles HTTP protocol.  Also handles
 * routing and static files.
 */
public final class WebFramework {

    private final Constants constants;
    private final UnderInvestigation underInvestigation;
    private final InputStreamUtils inputStreamUtils;
    private final StopwatchUtils stopWatchUtils;
    private final BodyProcessor bodyProcessor;
    /**
     * This is a variable storing a pseudo-random (non-secure) number
     * that is shown to users when a serious error occurs, which
     * will also be put in the logs, to make finding it easier.
     */
    private final Random randomErrorCorrelationId;
    private final FileUtils fileUtils;

    /**
     * This is used as a key when registering endpoints
     */
    record MethodPath(RequestLine.Method method, String path) { }

    /**
     * The list of paths that our system is registered to handle.
     */
    private final Map<MethodPath, Function<Request, Response>> registeredDynamicPaths;

    /**
     * These are registrations for paths that partially match, for example,
     * if the client sends us GET /.well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX
     * and we want to match ".well-known/acme-challenge"
     */
    private final Map<MethodPath, Function<Request, Response>> registeredPartialPaths;

    // This is just used for testing.  If it's null, we use the real time.
    private final ZonedDateTime overrideForDateTime;
    private final FullSystem fs;
    private final ILogger logger;
    private final Context context;

    /**
     * This is the brains of how the server responds to web clients.  Whatever
     * code lives here will be inserted into a slot within the server code.
     * See {@link Server#start(ExecutorService, ThrowingConsumer)}
     *
     * @param handlerFinder bear with me...  This is a function, that takes a {@link RequestLine}, and
     *                      returns a {@link Function} that handles the {@link Request} -> {@link Response}.
     *                      Normally, you would just use {@link #makePrimaryHttpHandler()} and the default code at
     *                      {@link #findEndpointForThisStartline(RequestLine)} would be called.  However, you can provide
     *                      a handler here if you want to override that behavior, for example in tests when
     *                      you want a bit more control.
     *                      <br>
     *                      The common case definition of this is found at {@link #findEndpointForThisStartline}
     */
    ThrowingConsumer<ISocketWrapper, IOException> makePrimaryHttpHandler(Function<RequestLine, Function<Request, Response>> handlerFinder) {

        // build the handler

        return sw -> {
            try (sw) {

                // if we recognize this client as an attacker, dump them.
                ITheBrig theBrig = (fs != null && fs.getTheBrig() != null) ? fs.getTheBrig() : null;
                if (theBrig != null) {
                    String remoteClient = sw.getRemoteAddr();
                    if (theBrig.isInJail(remoteClient + "_vuln_seeking")) {
                        // if this client is a vulnerability seeker, just dump them unceremoniously
                        logger.logDebug(() -> "closing the socket on " + remoteClient);
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
                    var sl = RequestLine.EMPTY(context).extractStartLine(rawStartLine);
                    logger.logTrace(() -> sw + ": StartLine received: " + sl.toString());
                    if (sl.getRawValue().isBlank()) {
                        /*
                        if we get in here, it means the client sent nothing in the spot
                        where the Start Line should have been - therefore, we're not
                        dealing with a kosher request at all. Bail.
                         */
                        return;
                    }

                    String suspiciousClues = underInvestigation.isLookingForSuspiciousPaths(sl.getPathDetails().isolatedPath());
                    if (suspiciousClues.length() > 0 && theBrig != null) {
                        logger.logDebug(() -> sw.getRemoteAddr() + " is looking for a vulnerability, for this: " + suspiciousClues);
                        theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", constants.VULN_SEEKING_JAIL_DURATION);
                        return;
                    }

                    /****************************************
                     *   Set up some important variables
                     ***************************************/
                    // The request we received from the client
                    Request clientRequest = Request.EMPTY(context);
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
                    if (sl.getVersion() == HttpVersion.ONE_DOT_ZERO) {
                        isKeepAlive = hi.hasKeepAlive();
                    } else if (sl.getVersion() == HttpVersion.ONE_DOT_ONE) {
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
                    Function<Request, Response> endpoint = handlerFinder.apply(sl);
                    if (endpoint == null) {
                        resultingResponse = new Response(_404_NOT_FOUND);
                    } else {
                        var handlerStopwatch = new StopwatchUtils().startTimer();
                        try {
                            clientRequest = new Request(hi, sl, body, sw.getRemoteAddr());
                            resultingResponse = endpoint.apply(clientRequest);
                        } catch (Exception ex) {
                            // if an error happens while running an endpoint's code, this is the
                            // last-chance handling of that error where we return a 500 and a
                            // random code to the client, so a developer can find the detailed
                            // information in the logs, which have that same value.
                            int randomNumber = randomErrorCorrelationId.nextInt();
                            logger.logAsyncError(() -> "error while running endpoint " + endpoint + ". Code: " + randomNumber + ". Error: " + StacktraceUtils.stackTraceToString(ex));
                            resultingResponse = new Response(_500_INTERNAL_SERVER_ERROR, "Server error: " + randomNumber, Map.of("Content-Type", "text/plain;charset=UTF-8"));
                        }
                        logger.logTrace(() -> String.format("handler processing of %s %s took %d millis", sw, sl, handlerStopwatch.stopTimer()));
                    }

                    String statusLineAndHeaders = convertResponseToString(clientRequest, resultingResponse, isKeepAlive);

                    // Here is where the bytes actually go out on the socket
                    String response = statusLineAndHeaders + HTTP_CRLF;

                    logger.logTrace(() -> "Sending headers back: " + response);
                    sw.send(response);

                    if (clientRequest.requestLine().getMethod() == RequestLine.Method.HEAD) {
                        Request finalClientRequest = clientRequest;
                        logger.logDebug(() -> "client " + finalClientRequest.remoteRequester() +
                                " is requesting HEAD for "+ finalClientRequest.requestLine().getPathDetails().isolatedPath() +
                                ".  Excluding body from response");
                    } else {
                        sw.send(resultingResponse.body());
                    }
                    logger.logTrace(() -> String.format("full processing (including communication time) of %s %s took %d millis", sw, sl, fullStopwatch.stopTimer()));

                    if (! isKeepAlive) break;
                }
            }
        };
    }

    /**
     * This handler redirects all traffic to the HTTPS endpoint.
     * <br>
     * It is necessary to extract the target path, but that's all
     * the help we'll give.  We're not going to extract headers or
     * body, we'll just read the start line and then stop reading from them.
     */
    ThrowingConsumer<ISocketWrapper, IOException> makeRedirectHandler() {
        return sw -> {
            try (sw) {
                try (InputStream is = sw.getInputStream()) {

                    String rawStartLine = inputStreamUtils.readLine(is);
                 /*
                  if the rawStartLine is null, that means the client stopped talking.
                  See ISocketWrapper.readLine()
                  */
                    if (rawStartLine == null) {
                        return;
                    }

                    var sl = RequestLine.EMPTY(context).extractStartLine(rawStartLine);

                    // just ignore all the rest of the incoming lines.  TCP is duplex -
                    // we'll just start talking back the moment we understand the first line.
                    String date = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
                    String hostname = constants.HOST_NAME;
                    sw.send(
                            "HTTP/1.1 303 SEE OTHER" + HTTP_CRLF +
                                    "Date: " + date + HTTP_CRLF +
                                    "Server: minum" + HTTP_CRLF +
                                    "Location: https://" + hostname + "/" + sl.getPathDetails().isolatedPath() + HTTP_CRLF +
                                    HTTP_CRLF
                    );
                }
            }
        };
    }

    /**
     * Determine whether the headers in this HTTP message indicate that
     * a body is available to read
     */
    boolean isThereIsABody(Headers hi) {
        // if the client sent us a content-type header at all...
        if (!hi.contentType().isBlank()) {
            // if the content-length is greater than 0, we've got a body
            if (hi.contentLength() > 0) return true;

            // if the transfer-encoding header is set to chunked, we have a body
            List<String> transferEncodingHeaders = hi.valueByKey("transfer-encoding");
            if (transferEncodingHeaders != null && transferEncodingHeaders.stream().anyMatch(x -> x.equalsIgnoreCase("chunked"))) return true;
        }
        // otherwise, no body we recognize
        return false;
    }

    /**
     * This is where our strongly-typed {@link Response} gets converted
     * to a string and sent on the socket.
     */
    private String convertResponseToString(Request request, Response response, boolean isKeepAlive) {
        String date = Objects.requireNonNullElseGet(overrideForDateTime, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        StringBuilder stringBuilder = new StringBuilder();

        // add the status line
        stringBuilder.append( "HTTP/1.1 " + response.statusCode().code + " " + response.statusCode().shortDescription + HTTP_CRLF );

        // add the headers
        stringBuilder
                .append( "Date: " + date + HTTP_CRLF )
                .append( "Server: minum" + HTTP_CRLF )
                .append(  response.extraHeaders().entrySet().stream().map(x -> x.getKey() + ": " + x.getValue() + HTTP_CRLF).collect(Collectors.joining()));

        // check the correctness of the content-type header versus the data length (if any data, that is)
        boolean hasContentType = response.extraHeaders().entrySet().stream().anyMatch(x -> x.getKey().toLowerCase(Locale.ROOT).equals("content-type"));

        // if there *is* data, we had better be returning a content type
        if (response.body().length > 0) {
            mustBeTrue(hasContentType, "a Content-Type header must be specified in the Response object if it returns data. Response details: " + response + " Request: " + request);
        }

        /*
        The rules regarding the content-length header are byzantine.  Even in the cases
        where you aren't returning anything, servers can use this header to determine when the
        response is finished.

        There are a few rules when you MUST include it, MUST NOT, blah blah blah, but I'm
        not following that stuff too closely because this code skips a lot of the spec.
        For example, we don't handle OPTIONS or return any 1xx response types.

        See https://www.rfc-editor.org/rfc/rfc9110.html#name-content-length
         */
        stringBuilder.append("Content-Length: " + response.body().length + HTTP_CRLF );

        // if we're a keep-alive connection, reply with a keep-alive header
        if (isKeepAlive) {
            stringBuilder.append("Keep-Alive: timeout=" + constants.KEEP_ALIVE_TIMEOUT_SECONDS + HTTP_CRLF);
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
    ThrowingConsumer<ISocketWrapper, IOException> makePrimaryHttpHandler() {
        return makePrimaryHttpHandler(this::findEndpointForThisStartline);
    }

    /**
     * Looks through the mappings of {@link MethodPath} and path to registered endpoints
     * or the static cache and returns the appropriate one (If we
     * do not find anything, return null)
     */
    Function<Request, Response> findEndpointForThisStartline(RequestLine sl) {
        Function<Request, Response> handler;
        logger.logTrace(() -> "Seeking a handler for " + sl);

        // first we check if there's a simple direct match
        String requestedPath = sl.getPathDetails().isolatedPath().toLowerCase(Locale.ROOT);

        // if the user is asking for a HEAD request, they want to run a GET command
        // but don't want the body.  We'll simply exclude sending the body, later on, when returning the data
        RequestLine.Method method = sl.getMethod() == RequestLine.Method.HEAD ? RequestLine.Method.GET : sl.getMethod();

        MethodPath key = new MethodPath(method, requestedPath);
        handler = registeredDynamicPaths.get(key);

        if (handler == null) {
            logger.logTrace(() -> "No direct handler found.  looking for a partial match for " + requestedPath);
            handler = findHandlerByPartialMatch(sl);
        }

        if (handler == null) {
            logger.logTrace(() -> "No partial match found, checking files on disk for " + requestedPath );
            handler = findHandlerByFilesOnDisk(sl);
        }

        // we'll return this, and it could be a null.
        return handler;
    }

    /**
     * last ditch effort - look on disk.  This response will either
     * be the file to return, or null if we didn't find anything.
     */
    private Function<Request, Response> findHandlerByFilesOnDisk(RequestLine sl) {
        if (sl.getMethod() != RequestLine.Method.GET) {
            return null;
        }
        String requestedPath = sl.getPathDetails().isolatedPath();
        Response response = fileUtils.readStaticFile(requestedPath);
        if (response != null) {
            return request -> response;
        } else {
            return null;
        }
    }

    /**
     * let's see if we can match the registered paths against a **portion** of the startline
     */
    Function<Request, Response> findHandlerByPartialMatch(RequestLine sl) {
        String requestedPath = sl.getPathDetails().isolatedPath();
        var methodPathFunctionEntry = registeredPartialPaths.entrySet().stream()
                .filter(x -> requestedPath.startsWith(x.getKey().path()) &&
                        x.getKey().method().equals(sl.getMethod()))
                .findFirst().orElse(null);
        if (methodPathFunctionEntry != null) {
            return methodPathFunctionEntry.getValue();
        } else {
            return null;
        }
    }

    /**
     * This constructor is used for the real production system
     */
    WebFramework(Context context) {
        this(context, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param overrideForDateTime for those test cases where we need to control the time
     */
    WebFramework(Context context, ZonedDateTime overrideForDateTime) {
        this.fs = context.getFullSystem();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.overrideForDateTime = overrideForDateTime;
        this.registeredDynamicPaths = new HashMap<>();
        this.registeredPartialPaths = new HashMap<>();
        this.context = context;
        this.underInvestigation = new UnderInvestigation(constants);
        this.inputStreamUtils = context.getInputStreamUtils();
        this.fileUtils = context.getFileUtils();
        this.stopWatchUtils = new StopwatchUtils();
        this.bodyProcessor = new BodyProcessor(context);
        // This random value is purely to help provide correlation betwee
        // error messages in the UI and error logs.  There are no security concerns.
        this.randomErrorCorrelationId = new Random();
    }

    /**
     * Add a new handler in the web application for a combination
     * of a {@link RequestLine.Method}, a path, and then provide
     * the code to handle a request.
     * <br>
     * Note that the path text expected is *after* the first forward slash,
     * so for example with {@code http://foo.com/mypath}, you provide us "mypath"
     * here.
     */
    public void registerPath(RequestLine.Method method, String pathName, Function<Request, Response> webHandler) {
        registeredDynamicPaths.put(new MethodPath(method, pathName), webHandler);
    }

    /**
     * Similar to {@link #registerPath(RequestLine.Method, String, Function)} except that the paths
     * registered here may be partially matched.
     * <p>
     *     For example, if you register <pre>.well-known/acme-challenge</pre> then it
     *     can match a client request for <pre>.well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX</pre>
     * </p>
     * <p>
     *     What if I didn't have the ability to match partial paths? In
     * that case, if I tried <pre>GET .well-known/acme-challenge/sadoifpiefewsfae</pre>
     * I would get an {@link RequestLine.PathDetails#isolatedPath()} of
     * <pre>.well-known/acme-challenge/sadoifpiefewsfae</pre> which wouldn't
     * match anything I could statically register.
     * </p>
     * <p>
     *     Be careful here, be thoughtful - partial paths will
     * </p>
     */
    public void registerPartialPath(RequestLine.Method method, String pathName, Function<Request, Response> webHandler) {
        registeredPartialPaths.put(new MethodPath(method, pathName), webHandler);
    }

    /**
     * This allows users to add extra mappings
     * between file suffixes and mime types, in case
     * a user needs one that was not provided.
     * <p>
     *     This is made available through the
     *     web framework.
     * </p>
     * <p>
     *     Example:
     * </p>
     * <pre>
     * {@code webFramework.addMimeForSuffix().put("foo","text/foo")}
     * </pre>
     */
    public void addMimeForSuffix(String suffix, String mimeType) {
        fileUtils.getSuffixToMime().put(suffix, mimeType);
    }
}
