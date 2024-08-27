package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.security.ITheBrig;
import com.renomad.minum.security.UnderInvestigation;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
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

import static com.renomad.minum.utils.FileUtils.badFilePathPatterns;
import static com.renomad.minum.utils.Invariants.mustBeTrue;
import static com.renomad.minum.web.StatusLine.StatusCode.*;
import static com.renomad.minum.web.WebEngine.HTTP_CRLF;

/**
 * This class is responsible for the HTTP handling after socket connection.
 * <p>
 *     The public methods are for registering endpoints - code that will be
 *     run for a given combination of HTTP method and path.  See documentation
 *     for the methods in this class.
 * </p>
 */
public final class WebFramework {

    private final Constants constants;
    private final UnderInvestigation underInvestigation;
    private final IInputStreamUtils inputStreamUtils;
    private final IBodyProcessor bodyProcessor;
    /**
     * This is a variable storing a pseudo-random (non-secure) number
     * that is shown to users when a serious error occurs, which
     * will also be put in the logs, to make finding it easier.
     */
    private final Random randomErrorCorrelationId;
    private final RequestLine emptyRequestLine;

    public Map<String,String> getSuffixToMimeMappings() {
        return new HashMap<>(fileSuffixToMime);
    }

    /**
     * This is used as a key when registering endpoints
     */
    record MethodPath(RequestLine.Method method, String path) { }

    /**
     * The list of paths that our system is registered to handle.
     */
    private final Map<MethodPath, ThrowingFunction<IRequest, IResponse>> registeredDynamicPaths;

    /**
     * These are registrations for paths that partially match, for example,
     * if the client sends us GET /.well-known/acme-challenge/HGr8U1IeTW4kY_Z6UIyaakzOkyQgPr_7ArlLgtZE8SX
     * and we want to match ".well-known/acme-challenge"
     */
    private final Map<MethodPath, ThrowingFunction<IRequest, IResponse>> registeredPartialPaths;

    /**
     * A function that will be run instead of the ordinary business code. Has
     * provisions for running the business code as well.  See {@link #registerPreHandler(ThrowingFunction)}
     */
    private ThrowingFunction<PreHandlerInputs, IResponse> preHandler;

    /**
     * A function run after the ordinary business code
     */
    private ThrowingFunction<LastMinuteHandlerInputs, IResponse> lastMinuteHandler;

    private final IFileReader fileReader;
    private final Map<String, String> fileSuffixToMime;

    // This is just used for testing.  If it's null, we use the real time.
    private final ZonedDateTime overrideForDateTime;
    private final FullSystem fs;
    private final ILogger logger;

    /**
     * This is the minimum number of bytes in a text response to apply gzip.
     */
    private static final int MINIMUM_NUMBER_OF_BYTES_TO_COMPRESS = 2048;

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
                    // check if the user is seeming to attack us.
                    checkIfSuspiciousPath(sw, sl);

                    // React to what the user requested, generate a result
                    Headers hi = getHeaders(sw);
                    boolean isKeepAlive = determineIfKeepAlive(sl, hi, logger);
                    if (isThereIsABody(hi)) {
                        logger.logTrace(() -> "There is a body. Content-type is " + hi.contentType());
                    }
                    ProcessingResult result = processRequest(sw, sl, hi);
                    IRequest request = result.clientRequest();
                    Response response = (Response)result.resultingResponse();

                    // calculate proper headers for the response
                    StringBuilder headerStringBuilder = addDefaultHeaders(response);
                    addOptionalExtraHeaders(response, headerStringBuilder);
                    addKeepAliveTimeout(isKeepAlive, headerStringBuilder);

                    // inspect the response being sent, see whether we can compress the data.
                    Response adjustedResponse = potentiallyCompress(request.getHeaders(), response, headerStringBuilder);
                    applyContentLength(headerStringBuilder, adjustedResponse.getBodyLength());
                    confirmBodyHasContentType(request, response);

                    // send the headers
                    sw.send(headerStringBuilder.append(HTTP_CRLF).toString());

                    // if the user sent a HEAD request, we send everything back except the body.
                    // even though we skip the body, this requires full processing to get the
                    // numbers right, like content-length.
                    if (request.getRequestLine().getMethod().equals(RequestLine.Method.HEAD)) {
                        logger.logDebug(() -> "client " + request.getRemoteRequester() +
                                " is requesting HEAD for "+ request.getRequestLine().getPathDetails().getIsolatedPath() +
                                ".  Excluding body from response");
                    } else {
                        // send the body
                        adjustedResponse.sendBody(sw);
                    }
                    // print how long this processing took
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

    /**
     * Logic for how to process an incoming request.  For example, did the developer
     * write a function to handle this? Is it a request for a static file, like an image
     * or script?  Did the user provide a "pre" or "post" handler?
     */
    ProcessingResult processRequest(
            ISocketWrapper sw,
            RequestLine requestLine,
            Headers requestHeaders) throws Exception {
        IRequest clientRequest = new Request(requestHeaders, requestLine, sw.getRemoteAddr(), sw, bodyProcessor);
        IResponse response;
        ThrowingFunction<IRequest, IResponse> endpoint = findEndpointForThisStartline(requestLine, requestHeaders);
        if (endpoint == null) {
            response = Response.buildLeanResponse(CODE_404_NOT_FOUND);
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
                response = Response.buildResponse(CODE_500_INTERNAL_SERVER_ERROR, Map.of("Content-Type", "text/plain;charset=UTF-8"), "Server error: " + randomNumber);
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

    record ProcessingResult(IRequest clientRequest, IResponse resultingResponse) { }

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
        List<String> allHeaders = Headers.getAllHeaders(sw.getInputStream(), inputStreamUtils);
        Headers hi = new Headers(allHeaders);
        logger.logTrace(() -> "The headers are: " + hi.getHeaderStrings());
        return hi;
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
        logger.logTrace(() -> sw + ": raw request line received: " + rawStartLine);
        RequestLine rl = new RequestLine(
                RequestLine.Method.NONE,
                PathDetails.empty,
                HttpVersion.NONE,
                "", logger);
        RequestLine extractedRequestLine = rl.extractRequestLine(rawStartLine);
        logger.logTrace(() -> sw + ": RequestLine has been derived: " + extractedRequestLine);
        return extractedRequestLine;
    }

    void checkIfSuspiciousPath(ISocketWrapper sw, RequestLine requestLine) {
        String suspiciousClues = underInvestigation.isLookingForSuspiciousPaths(
                requestLine.getPathDetails().getIsolatedPath());
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
     * Prepare some of the basic server response headers, like the status code, the
     * date-time stamp, the server name.
     */
    private StringBuilder addDefaultHeaders(IResponse response) {

        String date = Objects.requireNonNullElseGet(overrideForDateTime, () -> ZonedDateTime.now(ZoneId.of("UTC"))).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        // we'll store the status line and headers in this
        StringBuilder headerStringBuilder = new StringBuilder();


        // add the status line
        headerStringBuilder.append("HTTP/1.1 ").append(response.getStatusCode().code).append(" ").append(response.getStatusCode().shortDescription).append(HTTP_CRLF);

        // add a date-timestamp
        headerStringBuilder.append("Date: ").append(date).append(HTTP_CRLF);

        // add the server name
        headerStringBuilder.append("Server: minum").append(HTTP_CRLF);

        return headerStringBuilder;
    }

    /**
     * Add extra headers specified by the business logic (set by the developer)
     */
    private static void addOptionalExtraHeaders(IResponse response, StringBuilder stringBuilder) {
        stringBuilder.append(
                response.getExtraHeaders().entrySet().stream()
                .map(x -> x.getKey() + ": " + x.getValue() + HTTP_CRLF)
                .collect(Collectors.joining()));
    }

    /**
     * If a response body exists, it needs to have a content-type specified, or throw an exception.
     */
    static void confirmBodyHasContentType(IRequest request, Response response) {
        // check the correctness of the content-type header versus the data length (if any data, that is)
        boolean hasContentType = response.getExtraHeaders().entrySet().stream().anyMatch(x -> x.getKey().toLowerCase(Locale.ROOT).equals("content-type"));

        // if there *is* data, we had better be returning a content type
        if (response.getBodyLength() > 0) {
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
     * See <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-content-length">Content-Length in the HTTP spec</a>
     */
    private static void applyContentLength(StringBuilder stringBuilder, long bodyLength) {
        stringBuilder.append("Content-Length: ").append(bodyLength).append(HTTP_CRLF);
    }

    /**
     * This method will examine the request headers and response content-type, and
     * compress the outgoing data if necessary.
     */
    static Response potentiallyCompress(Headers headers, Response response, StringBuilder headerStringBuilder) throws IOException {
        // we may make modifications to the response body at this point, specifically
        // we may compress the data, if the client requested it.
        // see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-encoding
        List<String> acceptEncoding = headers.valueByKey("accept-encoding");

        // regardless of whether the client requests compression in their Accept-Encoding header,
        // if the data we're sending back is not of an appropriate type, we won't bother
        // compressing it.  Basically, we're going to compress plain text.
        Map.Entry<String, String> contentTypeHeader = SearchUtils.findExactlyOne(response.getExtraHeaders().entrySet().stream(), x -> x.getKey().equalsIgnoreCase("content-type"));

        if (contentTypeHeader != null) {
            String contentType = contentTypeHeader.getValue().toLowerCase(Locale.ROOT);
            if (contentType.contains("text/")) {
                return compressBodyIfRequested(response, acceptEncoding, headerStringBuilder, MINIMUM_NUMBER_OF_BYTES_TO_COMPRESS);
            }
        }
        return response;
    }

    /**
     * This method will examine the content-encoding headers, and if "gzip" is
     * requested by the client, we will replace the body bytes with compressed
     * bytes, using the GZIP compression algorithm, as long as the response body
     * is greater than minNumberBytes bytes.
     *
     * @param acceptEncoding headers sent by the client about what compression
     *                       algorithms will be understood.
     * @param stringBuilder  the string we are gradually building up to send back to
     *                       the client for the status line and headers. We'll use it
     *                       here if we need to append a content-encoding - that is,
     *                       if we successfully compress data as gzip.
     * @param minNumberBytes number of bytes must be larger than this to compress.
     */
    static Response compressBodyIfRequested(Response response, List<String> acceptEncoding, StringBuilder stringBuilder, int minNumberBytes) throws IOException {
        String allContentEncodingHeaders = acceptEncoding != null ? String.join(";", acceptEncoding) : "";
        if (response.getBodyLength() >= minNumberBytes && acceptEncoding != null && allContentEncodingHeaders.contains("gzip")) {
            stringBuilder.append("Content-Encoding: gzip" + HTTP_CRLF);
            stringBuilder.append("Vary: accept-encoding" + HTTP_CRLF);
            return response.compressBody();
        }
        return response;
    }

    /**
     * Looks through the mappings of {@link MethodPath} and path to registered endpoints
     * or the static cache and returns the appropriate one (If we
     * do not find anything, return null)
     */
    ThrowingFunction<IRequest, IResponse> findEndpointForThisStartline(RequestLine sl, Headers requestHeaders) {
        ThrowingFunction<IRequest, IResponse> handler;
        logger.logTrace(() -> "Seeking a handler for " + sl);

        // first we check if there's a simple direct match
        String requestedPath = sl.getPathDetails().getIsolatedPath().toLowerCase(Locale.ROOT);

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
            handler = findHandlerByFilesOnDisk(sl, requestHeaders);
        }

        // we'll return this, and it could be a null.
        return handler;
    }

    /**
     * last ditch effort - look on disk.  This response will either
     * be the file to return, or null if we didn't find anything.
     */
    private ThrowingFunction<IRequest, IResponse> findHandlerByFilesOnDisk(RequestLine sl, Headers requestHeaders) {
        if (sl.getMethod() != RequestLine.Method.GET && sl.getMethod() != RequestLine.Method.HEAD) {
            return null;
        }
        String requestedPath = sl.getPathDetails().getIsolatedPath();
        IResponse response = readStaticFile(requestedPath, requestHeaders);
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
    IResponse readStaticFile(String path, Headers requestHeaders) {
        if (badFilePathPatterns.matcher(path).find()) {
            logger.logDebug(() -> String.format("Bad path requested at readStaticFile: %s", path));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }
        String mimeType = null;

        try {
            Path staticFilePath = Path.of(constants.staticFilesDirectory).resolve(path);
            if (!Files.isRegularFile(staticFilePath)) {
                logger.logDebug(() -> String.format("No readable file found at %s", path));
                return Response.buildLeanResponse(CODE_404_NOT_FOUND);
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

            if (Files.size(staticFilePath) < 100_000) {
                var fileContents = fileReader.readFile(staticFilePath.toString());
                return createOkResponseForStaticFiles(fileContents, mimeType);
            } else {
                return createOkResponseForLargeStaticFiles(mimeType, staticFilePath, requestHeaders);
            }

        } catch (IOException e) {
            logger.logAsyncError(() -> String.format("Error while reading file: %s. %s", path, StacktraceUtils.stackTraceToString(e)));
            return Response.buildLeanResponse(CODE_400_BAD_REQUEST);
        }
    }

    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     */
    private IResponse createOkResponseForStaticFiles(byte[] fileContents, String mimeType) {
        var headers = Map.of(
                "cache-control", "max-age=" + constants.staticFileCacheTime,
                "content-type", mimeType);

        return Response.buildResponse(
                CODE_200_OK,
                headers,
                fileContents);
    }

    /**
     * All static responses will get a cache time of STATIC_FILE_CACHE_TIME seconds
     */
    private IResponse createOkResponseForLargeStaticFiles(String mimeType, Path filePath, Headers requestHeaders) throws IOException {
        var headers = Map.of(
                "cache-control", "max-age=" + constants.staticFileCacheTime,
                "content-type", mimeType,
                "Accept-Ranges", "bytes"
                );

        return Response.buildLargeFileResponse(
                headers,
                filePath.toString(),
                requestHeaders
                );
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
    ThrowingFunction<IRequest, IResponse> findHandlerByPartialMatch(RequestLine sl) {
        String requestedPath = sl.getPathDetails().getIsolatedPath();
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
        this.underInvestigation = new UnderInvestigation(constants);
        this.inputStreamUtils = new InputStreamUtils(constants.maxReadLineSizeBytes);
        this.bodyProcessor = new BodyProcessor(context);

        // This random value is purely to help provide correlation between
        // error messages in the UI and error logs.  There are no security concerns.
        this.randomErrorCorrelationId = new Random();
        this.emptyRequestLine = RequestLine.EMPTY;

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
     * so for example with {@code http://foo.com/mypath}, provide "mypath" as the path.
     */
    public void registerPath(RequestLine.Method method, String pathName, ThrowingFunction<IRequest, IResponse> webHandler) {
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
    public void registerPartialPath(RequestLine.Method method, String pathName, ThrowingFunction<IRequest, IResponse> webHandler) {
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
     *      webFramework.registerPreHandler(preHandlerInputs -> preHandlerCode(preHandlerInputs, auth, context));
     *
     *      ...
     *
     *      private IResponse preHandlerCode(PreHandlerInputs preHandlerInputs, AuthUtils auth, Context context) throws Exception {
     *          int secureServerPort = context.getConstants().secureServerPort;
     *          Request request = preHandlerInputs.clientRequest();
     *          ThrowingFunction<IRequest, IResponse> endpoint = preHandlerInputs.endpoint();
     *          ISocketWrapper sw = preHandlerInputs.sw();
     *
     *          // log all requests
     *          logger.logTrace(() -> String.format("Request: %s by %s",
     *              request.requestLine().getRawValue(),
     *              request.remoteRequester())
     *          );
     *
     *          // redirect to https if they are on the plain-text connection and the path is "login"
     *
     *          // get the path from the request line
     *          String path = request.getRequestLine().getPathDetails().getIsolatedPath();
     *
     *          // redirect to https on the configured secure port if they are on the plain-text connection and the path contains "login"
     *          if (path.contains("login") &&
     *              sw.getServerType().equals(HttpServerType.PLAIN_TEXT_HTTP)) {
     *              return Response.redirectTo("https://%s:%d/%s".formatted(sw.getHostName(), secureServerPort, path));
     *          }
     *
     *          // adjust behavior if non-authenticated and path includes "secure/"
     *          if (path.contains("secure/")) {
     *              AuthResult authResult = auth.processAuth(request);
     *              if (authResult.isAuthenticated()) {
     *                  return endpoint.apply(request);
     *              } else {
     *                  return Response.buildLeanResponse(CODE_403_FORBIDDEN);
     *              }
     *          }
     *
     *          // if the path does not include /secure, just move the request along unchanged.
     *          return endpoint.apply(request);
     *      }
     * }</pre>
     */
        public void registerPreHandler(ThrowingFunction<PreHandlerInputs, IResponse> preHandler) {
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
     *     private static IResponse lastMinuteHandlerCode(LastMinuteHandlerInputs inputs) {
     *         switch (inputs.response().statusCode()) {
     *             case CODE_404_NOT_FOUND -> {
     *                 return Response.buildResponse(
     *                         CODE_404_NOT_FOUND,
     *                         Map.of("Content-Type", "text/html; charset=UTF-8"),
     *                         "<p>No document was found</p>"));
     *             }
     *             case CODE_500_INTERNAL_SERVER_ERROR -> {
     *                 return Response.buildResponse(
     *                         CODE_500_INTERNAL_SERVER_ERROR,
     *                         Map.of("Content-Type", "text/html; charset=UTF-8"),
     *                         "<p>Server error occurred.</p>" ));
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
    public void registerLastMinuteHandler(ThrowingFunction<LastMinuteHandlerInputs, IResponse> lastMinuteHandler) {
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
        fileSuffixToMime.put(suffix, mimeType);
    }
}
