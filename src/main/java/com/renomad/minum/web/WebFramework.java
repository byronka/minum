package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.UnderInvestigation;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.*;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.CompressionUtils.gzipCompress;
import static com.renomad.minum.utils.FileUtils.badFilePathPatterns;
import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.web.StatusLine.StatusCode.*;
import static com.renomad.minum.web.WebEngine.HTTP_CRLF;

/**
 * Responsible for the HTTP handling after socket connection
 */
public final class WebFramework {

    private final Constants constants;
    private final UnderInvestigation underInvestigation;
    private final IInputStreamUtils inputStreamUtils;
    private final BodyProcessor bodyProcessor;
    /**
     * This is a variable storing a pseudo-random (non-secure) number
     * that is shown to users when a serious error occurs, which
     * will also be put in the logs, to make finding it easier.
     */
    private final Random randomErrorCorrelationId;
    private final RequestLine emptyRequestLine;

    /**
     * This is used as a key when registering endpoints
     */
    record MethodPath(RequestLine.Method method, String path) { }

    /**
     * The list of paths that our system is registered to handle.
     */
    private final Map<MethodPath, ThrowingFunction<Request, Response>> registeredDynamicPaths;

    /**
     * These are registrations for paths that partially match, for example,
     * if the client sends us GET /.well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX
     * and we want to match ".well-known/acme-challenge"
     */
    private final Map<MethodPath, ThrowingFunction<Request, Response>> registeredPartialPaths;

    /**
     * A function that will be run instead of the ordinary business code. Has
     * provisions for running the business code as well.  See {@link #registerPreHandler(ThrowingFunction)}
     */
    private ThrowingFunction<PreHandlerInputs, Response> preHandler;

    /**
     * A function run after the ordinary business code
     */
    private ThrowingFunction<LastMinuteHandlerInputs, Response> lastMinuteHandler;

    private final IFileReader fileReader;
    private final Map<String, String> fileSuffixToMime;

    // This is just used for testing.  If it's null, we use the real time.
    private final ZonedDateTime overrideForDateTime;
    private final FullSystem fs;
    private final ILogger logger;
    private final Context context;

    /**
     * This is the brains of how the server responds to web clients.  Whatever
     * code lives here will be inserted into a slot within the server code.
     */
    ThrowingRunnable makePrimaryHttpHandler(ISocketWrapper sw, ITheBrig theBrig) {

        return () -> {
            Thread.currentThread().setName("SocketWrapper thread for " + sw.getRemoteAddr());
            try (sw) {
                dumpIfAttacker(sw, fs);
                final var is = sw.getInputStream();

                // By default, browsers expect the server to run in keep-alive mode.
                // We'll break out later if we find that the browser doesn't do keep-alive
                while (true) {
                    final String rawStartLine = inputStreamUtils.readLine(is);
                    long startMillis = System.currentTimeMillis();
                    if (rawStartLine.isEmpty()) {
                        // here, the client connected, sent nothing, and closed.
                        // nothing to do but return.
                        logger.logTrace(() -> "rawStartLine was empty.  Returning.");
                        break;
                    }
                    final RequestLine sl = getProcessedRequestLine(sw, rawStartLine);

                    if (sl.equals(emptyRequestLine)) {
                        // here, the client sent something we cannot parse.
                        // nothing to do but return.
                        logger.logTrace(() -> "RequestLine was unparseable.  Returning.");
                        break;
                    }
                    checkIfSuspiciousPath(sw, sl);
                    Headers hi = getHeaders(sw);
                    boolean isKeepAlive = determineIfKeepAlive(sl, hi, logger);
                    Body body = determineIfBody(sw, hi);
                    ProcessingResult result = processRequest(sw, sl, hi, body);
                    PreparedResponse preparedResponse = prepareResponseData(
                            result.clientRequest(),
                            result.resultingResponse(),
                            isKeepAlive);
                    sendResponse(sw, preparedResponse, result);
                    long endMillis = System.currentTimeMillis();
                    logger.logTrace(() -> String.format("full processing (including communication time) of %s %s took %d millis", sw, sl, endMillis - startMillis));
                    if (!isKeepAlive) break;
                }
            } catch (SocketException | SocketTimeoutException ex) {
                handleReadTimedOut(sw, ex, logger);
            } catch (ForbiddenUseException ex) {
                handleForbiddenUse(sw, ex, logger, theBrig, constants.vulnSeekingJailDuration);
            } catch (IOException ex) {
                handleIOException(sw, ex, logger, theBrig, underInvestigation, constants.vulnSeekingJailDuration);
            }
        };
    }


    static void handleIOException(ISocketWrapper sw, IOException ex, ILogger logger, ITheBrig theBrig, UnderInvestigation underInvestigation, int vulnSeekingJailDuration ) {
        logger.logDebug(() -> ex.getMessage() + " (at Server.start)");
        String suspiciousClues = underInvestigation.isClientLookingForVulnerabilities(ex.getMessage());

        if (!suspiciousClues.isEmpty() && theBrig != null) {
            logger.logDebug(() -> sw.getRemoteAddr() + " is looking for vulnerabilities, for this: " + suspiciousClues);
            theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", vulnSeekingJailDuration);
        }
    }

    static void handleForbiddenUse(ISocketWrapper sw, ForbiddenUseException ex, ILogger logger, ITheBrig theBrig, int vulnSeekingJailDuration) {
        logger.logDebug(() -> sw.getRemoteAddr() + " is looking for vulnerabilities, for this: " + ex.getMessage());
        if (theBrig != null) {
            theBrig.sendToJail(sw.getRemoteAddr() + "_vuln_seeking", vulnSeekingJailDuration);
        } else {
            logger.logDebug(() -> "theBrig is null at handleForbiddenUse, will not store address in database");
        }
    }

    static void handleReadTimedOut(ISocketWrapper sw, IOException ex, ILogger logger) {
        /*
        if we close the application on the server side, there's a good
        likelihood a SocketException will come bubbling through here.
        NOTE:
          it seems that Socket closed is what we get when the client closes the connection in non-SSL, and conversely,
          if we are operating in secure (i.e. SSL/TLS) mode, we get "an established connection..."
        */
        if (ex.getMessage().equals("Read timed out")) {
            logger.logTrace(() -> "Read timed out - remote address: " + sw.getRemoteAddrWithPort());
        } else {
            logger.logDebug(() -> ex.getMessage() + " - remote address: " + sw.getRemoteAddrWithPort());
        }
    }


    void sendResponse(ISocketWrapper sw, PreparedResponse preparedResponse, ProcessingResult result) throws IOException {
        // Here is where the bytes actually go out on the socket
        String statusLineAndHeaders = preparedResponse.statusLineAndHeaders() + HTTP_CRLF;

        logger.logTrace(() -> "Sending headers back: " + statusLineAndHeaders);
        sw.send(statusLineAndHeaders);

        if (result.clientRequest().requestLine().getMethod().equals(RequestLine.Method.HEAD)) {
            Request finalClientRequest = result.clientRequest();
            logger.logDebug(() -> "client " + finalClientRequest.remoteRequester() +
                    " is requesting HEAD for "+ finalClientRequest.requestLine().getPathDetails().isolatedPath() +
                    ".  Excluding body from response");
        } else {
            sw.send(preparedResponse.body());
        }
    }

    ProcessingResult processRequest(
            ISocketWrapper sw,
            RequestLine requestLine,
            Headers hi,
            Body body) throws Exception {
        Request clientRequest = new Request(hi, requestLine, body, sw.getRemoteAddr());
        Response response;
        ThrowingFunction<Request, Response> endpoint = findEndpointForThisStartline(requestLine);
        if (endpoint == null) {
            response = new Response(CODE_404_NOT_FOUND);
        } else {
            long millisAtStart = System.currentTimeMillis();
            try {
                if (preHandler != null) {
                    response = preHandler.apply(new PreHandlerInputs(clientRequest, endpoint, sw));
                } else {
                    response = endpoint.apply(clientRequest);
                }
            } catch (Exception ex) {
                // if an error happens while running an endpoint's code, this is the
                // last-chance handling of that error where we return a 500 and a
                // random code to the client, so a developer can find the detailed
                // information in the logs, which have that same value.
                int randomNumber = randomErrorCorrelationId.nextInt();
                logger.logAsyncError(() -> "error while running endpoint " + endpoint + ". Code: " + randomNumber + ". Error: " + StacktraceUtils.stackTraceToString(ex));
                response = new Response(CODE_500_INTERNAL_SERVER_ERROR, "Server error: " + randomNumber, Map.of("Content-Type", "text/plain;charset=UTF-8"));
            }
            long millisAtEnd = System.currentTimeMillis();
            logger.logTrace(() -> String.format("handler processing of %s %s took %d millis", sw, requestLine, millisAtEnd - millisAtStart));
        }

        // if the user has chosen to customize the response based on status code, that will
        // be applied now, and it will override the previous response.
        if (lastMinuteHandler != null) {
            response = lastMinuteHandler.apply(new LastMinuteHandlerInputs(clientRequest, response));
        }

        return new ProcessingResult(clientRequest, response);
    }

    record ProcessingResult(Request clientRequest, Response resultingResponse) { }

    private Headers getHeaders(ISocketWrapper sw) {
    /*
       next we will read the headers (e.g. Content-Type: foo/bar) one-by-one.

       the headers tell us vital information about the
       body.  If, for example, we're getting a POST and receiving a
       www form url encoded, there will be a header of "content-length"
       that will mention how many bytes to read.  On the other hand, if
       we're receiving a multipart, there will be no content-length, but
       the content-type will include the boundary string.
    */
        var headers = Headers.make(context);
        Headers hi = headers.extractHeaderInformation(sw.getInputStream());
        logger.logTrace(() -> "The headers are: " + hi.getHeaderStrings());
        return hi;
    }

    /**
     * Determine whether there is a body (a block of data) in this request
     */
    private Body determineIfBody(ISocketWrapper sw, Headers hi) {
        Body body = Body.EMPTY;
        if (isThereIsABody(hi)) {
            logger.logTrace(() -> "There is a body. Content-type is " + hi.contentType());
            body = bodyProcessor.extractData(sw.getInputStream(), hi);
            Body finalBody = body;

            logger.logTrace(() -> getBodyStringForTraceLog(finalBody.asString()));
        }
        return body;
    }

    static String getBodyStringForTraceLog(String bodyString) {
        if (bodyString == null) {
            return "The body was null";
        }
        String possiblyTruncatedBody;
        if (bodyString.length() > 50) {
            possiblyTruncatedBody = bodyString.substring(0,50);
        } else {
            possiblyTruncatedBody = bodyString;
        }
        return "The body is: " + possiblyTruncatedBody;
    }

    /**
     * determine if we are in a keep-alive connection
     */
    static boolean determineIfKeepAlive(RequestLine sl, Headers hi, ILogger logger) {
        boolean isKeepAlive = false;
        if (sl.getVersion() == HttpVersion.ONE_DOT_ZERO) {
            isKeepAlive = hi.hasKeepAlive();
        } else if (sl.getVersion() == HttpVersion.ONE_DOT_ONE) {
            isKeepAlive = ! hi.hasConnectionClose();
        }
        boolean finalIsKeepAlive = isKeepAlive;
        logger.logTrace(() -> "Is this a keep-alive connection? " + finalIsKeepAlive);
        return isKeepAlive;
    }

    RequestLine getProcessedRequestLine(ISocketWrapper sw, String rawStartLine) {
        logger.logTrace(() -> sw + ": raw startline received: " + rawStartLine);
        RequestLine sl = RequestLine.empty(context).extractRequestLine(rawStartLine);
        logger.logTrace(() -> sw + ": StartLine received: " + sl);
        return sl;
    }

    void checkIfSuspiciousPath(ISocketWrapper sw, RequestLine requestLine) {
        String suspiciousClues = underInvestigation.isLookingForSuspiciousPaths(
                requestLine.getPathDetails().isolatedPath());
        if (!suspiciousClues.isEmpty()) {
            String msg = sw.getRemoteAddr() + " is looking for a vulnerability, for this: " + suspiciousClues;
            throw new ForbiddenUseException(msg);
        }
    }

    /**
     * This code confirms our objects are valid before calling
     * to {@link #dumpIfAttacker(ISocketWrapper, ITheBrig)}.
     * @return true if successfully called to subsequent method, false otherwise.
     */
    boolean dumpIfAttacker(ISocketWrapper sw, FullSystem fs) {
        if (fs == null) {
            return false;
        } else if (fs.getTheBrig() == null) {
            return false;
        } else {
            dumpIfAttacker(sw, fs.getTheBrig());
            return true;
        }
    }

    void dumpIfAttacker(ISocketWrapper sw, ITheBrig theBrig) {
        String remoteClient = sw.getRemoteAddr();
        if (theBrig.isInJail(remoteClient + "_vuln_seeking")) {
            // if this client is a vulnerability seeker, throw an exception,
            // causing them to get dumped unceremoniously
            String message = "closing the socket on " + remoteClient + " due to being found in the brig";
            logger.logDebug(() -> message);
            throw new ForbiddenUseException(message);
        }
    }

    /**
     * Determine whether the headers in this HTTP message indicate that
     * a body is available to read
     */
    static boolean isThereIsABody(Headers hi) {
        // if the client sent us a content-type header at all...
        if (!hi.contentType().isBlank()) {
            // if the content-length is greater than 0, we've got a body
            if (hi.contentLength() > 0) return true;

            // if the transfer-encoding header is set to chunked, we have a body
            List<String> transferEncodingHeaders = hi.valueByKey("transfer-encoding");
            return transferEncodingHeaders != null && transferEncodingHeaders.stream().anyMatch(x -> x.equalsIgnoreCase("chunked"));
        }
        // otherwise, no body we recognize
        return false;
    }

    /**
     * This is where our strongly-typed {@link Response} gets converted
     * to a string and sent on the socket.
     */
    private PreparedResponse prepareResponseData(Request request, Response response, boolean isKeepAlive) {
        String date = Objects.requireNonNullElseGet(overrideForDateTime, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        // we'll store the status line and headers in this
        StringBuilder headerStringBuilder = new StringBuilder();
        VaryHeader varyHeader = new VaryHeader();

        // add the status line
        headerStringBuilder.append("HTTP/1.1 ").append(response.statusCode().code).append(" ").append(response.statusCode().shortDescription).append(HTTP_CRLF);

        // add a date-timestamp
        headerStringBuilder.append("Date: ").append(date).append(HTTP_CRLF);

        // add the server name
        headerStringBuilder.append("Server: minum").append(HTTP_CRLF);

        addOptionalExtraHeaders(response, headerStringBuilder);

        addKeepAliveTimeout(isKeepAlive, headerStringBuilder);

        // body stuff
        confirmBodyHasContentType(request, response);
        byte[] bodyBytes = potentiallyCompress(request.headers(), response, headerStringBuilder, varyHeader);
        applyContentLength(headerStringBuilder, bodyBytes);
        headerStringBuilder.append(varyHeader).append(HTTP_CRLF);

        return new PreparedResponse(headerStringBuilder.toString(), bodyBytes);
    }

    /**
     * Add extra headers specified by the business logic
     */
    private static void addOptionalExtraHeaders(Response response, StringBuilder stringBuilder) {
        stringBuilder.append(
                response.extraHeaders().entrySet().stream()
                .map(x -> x.getKey() + ": " + x.getValue() + HTTP_CRLF)
                .collect(Collectors.joining()));
    }

    /**
     * If a response body exists, it needs to have a content-type specified, or throw an exception.
     */
    static void confirmBodyHasContentType(Request request, Response response) {
        // check the correctness of the content-type header versus the data length (if any data, that is)
        boolean hasContentType = response.extraHeaders().entrySet().stream().anyMatch(x -> x.getKey().toLowerCase(Locale.ROOT).equals("content-type"));

        // if there *is* data, we had better be returning a content type
        if (response.body().length > 0) {
            mustBeTrue(hasContentType, "a Content-Type header must be specified in the Response object if it returns data. Response details: " + response + " Request: " + request);
        }
    }

    /**
     * If this is a keep-alive communication, add a header specifying the
     * socket timeout for the browser.
     */
    private void addKeepAliveTimeout(boolean isKeepAlive, StringBuilder stringBuilder) {
        // if we're a keep-alive connection, reply with a keep-alive header
        if (isKeepAlive) {
            stringBuilder.append("Keep-Alive: timeout=").append(constants.keepAliveTimeoutSeconds).append(HTTP_CRLF);
        }
    }

    /**
     * The rules regarding the content-length header are byzantine.  Even in the cases
     * where you aren't returning anything, servers can use this header to determine when the
     * response is finished.
     * <br>
     * There are a few rules when you MUST include it, MUST NOT, blah blah blah, but I'm
     * not following that stuff too closely because this code skips a lot of the spec.
     * For example, we don't handle OPTIONS or return any 1xx response types.
     * <br>
     * See <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-content-length">Content-Length in the HTTP spec</a>
     */
    private static void applyContentLength(StringBuilder stringBuilder, byte[] bodyBytes) {
        stringBuilder.append("Content-Length: ").append(bodyBytes.length).append(HTTP_CRLF);
    }

    /**
     * This method will examine the request headers and response content-type, and
     * compress the outgoing data if necessary.
     */
    static byte[] potentiallyCompress(Headers headers, Response response, StringBuilder headerStringBuilder, VaryHeader varyHeader) {
        // we may make modifications to the response body at this point, specifically
        // we may compress the data, if the client requested it.
        // see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-encoding
        List<String> acceptEncoding = headers.valueByKey("accept-encoding");

        // regardless of whether the client requests compression in their Accept-Encoding header,
        // if the data we're sending back is not of an appropriate type, we won't bother
        // compressing it.  Basically, we're going to compress plain text.
        Map.Entry<String, String> contentTypeHeader = SearchUtils.findExactlyOne(
                response.extraHeaders().entrySet().stream(), x -> x.getKey().equalsIgnoreCase("content-type"));

        byte[] bodyBytes = response.body();
        if (contentTypeHeader != null) {
            String contentType = contentTypeHeader.getValue().toLowerCase();
            if (contentType.contains("text/")) {
                bodyBytes = compressBodyIfRequested(response.body(), acceptEncoding, headerStringBuilder, 2048);
                varyHeader.addHeader("accept-encoding");
            }
        }
        return bodyBytes;
    }

    /**
     * This method will examine the content-encoding headers, and if "gzip" is
     * requested by the client, we will replace the body bytes with compressed
     * bytes, using the GZIP compression algorithm, as long as the response body
     * is greater than minNumberBytes bytes.
     *
     * @param bodyBytes      the incoming bytes, not yet compressed
     * @param acceptEncoding headers sent by the client about what compression
     *                       algorithms will be understood.
     * @param stringBuilder  the string we are gradually building up to send back to
     *                       the client for the status line and headers. We'll use it
     *                       here if we need to append a content-encoding - that is,
     *                       if we successfully compress data as gzip.
     * @param minNumberBytes number of bytes must be larger than this to compress.
     */
    static byte[] compressBodyIfRequested(byte[] bodyBytes, List<String> acceptEncoding, StringBuilder stringBuilder, int minNumberBytes) {
        String allContentEncodingHeaders = acceptEncoding != null ? String.join(";", acceptEncoding) : "";
        if (bodyBytes.length >= minNumberBytes && acceptEncoding != null && allContentEncodingHeaders.contains("gzip")) {
            stringBuilder.append("Content-Encoding: gzip" + HTTP_CRLF);
            return gzipCompress(bodyBytes);
        } else {
            return bodyBytes;
        }
    }

    /**
     * Looks through the mappings of {@link MethodPath} and path to registered endpoints
     * or the static cache and returns the appropriate one (If we
     * do not find anything, return null)
     */
    ThrowingFunction<Request, Response> findEndpointForThisStartline(RequestLine sl) {
        ThrowingFunction<Request, Response> handler;
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
    private ThrowingFunction<Request, Response> findHandlerByFilesOnDisk(RequestLine sl) {
        if (sl.getMethod() != RequestLine.Method.GET && sl.getMethod() != RequestLine.Method.HEAD) {
            return null;
        }
        String requestedPath = sl.getPathDetails().isolatedPath();
        Response response = readStaticFile(requestedPath);
        return request -> response;
    }


    /**
     * Get a file from a path and create a response for it with a mime type.
     * <p>
     *     Parent directories are made unavailable by searching the path for
     *     bad characters.  See {@link FileUtils#badFilePathPatterns}
     * </p>
     *
     * @return a response with the file contents and caching headers and mime if valid.
     *  if the path has invalid characters, we'll return a "bad request" response.
     */
    Response readStaticFile(String path) {
        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at readStaticFile: %s", path));
            return new Response(CODE_400_BAD_REQUEST);
        }
        String mimeType = null;

        byte[] fileContents;
        try {
            Path staticFilePath = Path.of(constants.staticFilesDirectory).resolve(path);
            if (!Files.isRegularFile(staticFilePath)) {
                logger.logDebug(() -> String.format("No readable file found at %s", path));
                return new Response(CODE_404_NOT_FOUND);
            }
            fileContents = fileReader.readFile(staticFilePath.toString());
        } catch (IOException e) {
            logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
            return new Response(CODE_400_BAD_REQUEST);
        }

        // if the provided path has a dot in it, use that
        // to obtain a suffix for determining file type
        int suffixBeginIndex = path.lastIndexOf('.');
        if (suffixBeginIndex > 0) {
            String suffix = path.substring(suffixBeginIndex+1);
            mimeType = fileSuffixToMime.get(suffix);
        }

        // if we don't find any registered mime types for this
        // suffix, or if it doesn't have a suffix, set the mime type
        // to application/octet-stream
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        return createOkResponseForStaticFiles(fileContents, mimeType);
    }

    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     */
    private Response createOkResponseForStaticFiles(byte[] fileContents, String mimeType) {
        var headers = Map.of(
                "cache-control", "max-age=" + constants.staticFileCacheTime,
                "content-type", mimeType);

        return new Response(
                StatusLine.StatusCode.CODE_200_OK,
                headers,
                fileContents);
    }


    /**
     * These are the default starting values for mappings
     * between file suffixes and appropriate mime types
     */
    private void addDefaultValuesForMimeMap() {
        fileSuffixToMime.put("css", "text/css");
        fileSuffixToMime.put("js", "application/javascript");
        fileSuffixToMime.put("webp", "image/webp");
        fileSuffixToMime.put("jpg", "image/jpeg");
        fileSuffixToMime.put("jpeg", "image/jpeg");
        fileSuffixToMime.put("htm", "text/html");
        fileSuffixToMime.put("html", "text/html");
    }


    /**
     * let's see if we can match the registered paths against a **portion** of the startline
     */
    ThrowingFunction<Request, Response> findHandlerByPartialMatch(RequestLine sl) {
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
        this(context, null, null);
    }

    WebFramework(Context context, ZonedDateTime overrideForDateTime) {
        this(context, overrideForDateTime, null);
    }

    /**
     * This provides the ZonedDateTime as a parameter so we
     * can set the current date (for testing purposes)
     * @param overrideForDateTime for those test cases where we need to control the time
     */
    WebFramework(Context context, ZonedDateTime overrideForDateTime, IFileReader fileReader) {
        this.fs = context.getFullSystem();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.overrideForDateTime = overrideForDateTime;
        this.registeredDynamicPaths = new HashMap<>();
        this.registeredPartialPaths = new HashMap<>();
        this.context = context;
        this.underInvestigation = new UnderInvestigation(constants);
        this.inputStreamUtils = context.getInputStreamUtils();
        this.bodyProcessor = new BodyProcessor(context);

        // This random value is purely to help provide correlation betwee
        // error messages in the UI and error logs.  There are no security concerns.
        this.randomErrorCorrelationId = new Random();
        this.emptyRequestLine = RequestLine.empty(context);

        // this allows us to inject a IFileReader for deeper testing
        if (fileReader != null) {
            this.fileReader = fileReader;
        } else {
            this.fileReader = new FileReader(
                    LRUCache.getLruCache(constants.maxElementsLruCacheStaticFiles),
                    constants.useCacheForStaticFiles,
                    logger);
        }
        this.fileSuffixToMime = new HashMap<>();
        addDefaultValuesForMimeMap();
        readExtraMimeMappings(constants.extraMimeMappings);
    }


    /**
     * This getter allows users to add extra mappings
     * between file suffixes and mime types, in case
     * a user needs one that was not provided.
     */
    public Map<String,String> getSuffixToMime() {
        return fileSuffixToMime;
    }


    void readExtraMimeMappings(List<String> input) {
        if (input == null || input.isEmpty()) return;
        mustBeTrue(input.size() % 2 == 0, "input must be even (key + value = 2 items). Your input: " + input);

        for (int i = 0; i < input.size(); i += 2) {
            String fileSuffix = input.get(i);
            String mime = input.get(i+1);
            logger.logTrace(() -> "Adding mime mapping: " + fileSuffix + " -> " + mime);
            fileSuffixToMime.put(fileSuffix, mime);
        }
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
    public void registerPath(RequestLine.Method method, String pathName, ThrowingFunction<Request, Response> webHandler) {
        registeredDynamicPaths.put(new MethodPath(method, pathName), webHandler);
    }

    /**
     * Similar to {@link WebFramework#registerPath(RequestLine.Method, String, ThrowingFunction)} except that the paths
     * registered here may be partially matched.
     * <p>
     *     For example, if you register {@code .well-known/acme-challenge} then it
     *     can match a client request for {@code .well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX}
     * </p>
     * <p>
     *     Be careful here, be thoughtful - partial paths will match a lot, and may
     *     overlap with other URL's for your app, such as endpoints and static files.
     * </p>
     */
    public void registerPartialPath(RequestLine.Method method, String pathName, ThrowingFunction<Request, Response> webHandler) {
        registeredPartialPaths.put(new MethodPath(method, pathName), webHandler);
    }

    /**
     * Sets a handler to process all requests across the board.
     * <br>
     * <p>
     *     This is an <b>unusual</b> method.  Setting a handler here allows the user to run code of his
     * choosing before the regular business code is run.  Note that by defining this value, the ordinary
     * call to endpoint.apply(request) will not be run.
     * </p>
     * <p>Here is an example</p>
     * <pre>{@code
     *
     *      webFramework.registerPreHandler(preHandlerInputs -> preHandlerCode(preHandlerInputs, auth));
     *
     *      ...
     *
     *      private Response preHandlerCode(PreHandlerInputs preHandlerInputs, AuthUtils auth) throws Exception {
     *          // log all requests
     *          Request request = preHandlerInputs.clientRequest();
     *          ThrowingFunction<Request, Response> endpoint = preHandlerInputs.endpoint();
     *          ISocketWrapper sw = preHandlerInputs.sw();
     *
     *          logger.logTrace(() -> String.format("Request: %s by %s",
     *              request.requestLine().getRawValue(),
     *              request.remoteRequester())
     *          );
     *
     *          String path = request.requestLine().getPathDetails().isolatedPath();
     *
     *          // redirect to https if they are on the plain-text connection and the path is "whoops"
     *          if (path.contains("whoops") &&
     *              sw.getServerType().equals(HttpServerType.PLAIN_TEXT_HTTP)) {
     *              return Response.redirectTo("https://" + sw.getHostName() + "/" + path);
     *          }
     *
     *          // adjust behavior if non-authenticated and path includes "secure/"
     *          if (path.contains("secure/")) {
     *              AuthResult authResult = auth.processAuth(request);
     *              if (authResult.isAuthenticated()) {
     *                  return endpoint.apply(request);
     *              } else {
     *                  return new Response(CODE_403_FORBIDDEN);
     *              }
     *          }
     *
     *          // if the path does not include /secure, just move the request along unchanged.
     *          return endpoint.apply(request);
     *      }
     * }</pre>
     */
        public void registerPreHandler(ThrowingFunction<PreHandlerInputs, Response> preHandler) {
        this.preHandler = preHandler;
    }

    /**
     * Sets a handler to be executed after running the ordinary handler, just
     * before sending the response.
     * <p>
     *     This is an <b>unusual</b> method, so please be aware of its proper use. Its
     *     purpose is to allow the user to inject code to run after ordinary code, across
     *     all requests.
     * </p>
     * <p>
     *     For example, if the system would have returned a 404 NOT FOUND response,
     *     code can handle that situation in a switch case and adjust the response according
     *     to your programming.
     * </p>
     * <p>Here is an example</p>
     * <pre>{@code
     *
     *
     *      webFramework.registerLastMinuteHandler(TheRegister::lastMinuteHandlerCode);
     *
     * ...
     *
     *     private static Response lastMinuteHandlerCode(LastMinuteHandlerInputs inputs) {
     *         switch (inputs.response().statusCode()) {
     *             case CODE_404_NOT_FOUND -> {
     *                 return new Response(
     *                         StatusLine.StatusCode.CODE_404_NOT_FOUND,
     *                         "<p>No document was found</p>",
     *                         Map.of("Content-Type", "text/html; charset=UTF-8"));
     *             }
     *             case CODE_500_INTERNAL_SERVER_ERROR -> {
     *                 Response response = inputs.response();
     *                 return new Response(
     *                         StatusLine.StatusCode.CODE_500_INTERNAL_SERVER_ERROR,
     *                         "<p>Server error occurred.  A log entry with further information has been added with the following code . " + new String(response.body()) + "</p>",
     *                         Map.of("Content-Type", "text/html; charset=UTF-8"));
     *             }
     *             default -> {
     *                 return inputs.response();
     *             }
     *         }
     *     }
     * }
     * </pre>
     * @param lastMinuteHandler a function that will take a request and return a response, exactly like
     *                   we use in the other registration methods for this class.
     */
    public void registerLastMinuteHandler(ThrowingFunction<LastMinuteHandlerInputs, Response> lastMinuteHandler) {
        this.lastMinuteHandler = lastMinuteHandler;
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
        getSuffixToMime().put(suffix, mimeType);
    }
}
