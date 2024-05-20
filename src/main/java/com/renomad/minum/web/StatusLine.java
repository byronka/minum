package com.renomad.minum.web;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * This class represents the text that is sent back in a {@link Response}
 */
public record StatusLine(StatusCode status, HttpVersion version, String rawValue) {

    static final StatusLine EMPTY = new StatusLine(StatusCode.NULL, HttpVersion.NONE, "");

    /**
     * This is the regex used to analyze a status line sent by the server and
     * read by the client.  Servers will send messages like: "HTTP/1.1 200 OK" or "HTTP/1.1 500 Internal Server Error"
     */
    static final String statusLinePattern = "^HTTP/(...) (\\d{3}) (.*)$";
    static final Pattern statusLineRegex = Pattern.compile(statusLinePattern);

    /**
     * See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Status">Status Codes</a>
     */
    public enum StatusCode{

        /* Successful responses (200 – 299) */

        CODE_200_OK(200, "OK"),
        CODE_201_CREATED(201, "CREATED"),
        CODE_202_ACCEPTED(202, "ACCEPTED"),

        CODE_204_NO_CONTENT(204, "NO CONTENT"),

        /* Redirection messages (300 – 399) */

        CODE_301_MOVED_PERMANENTLY(301, "MOVED PERMANENTLY"),
        CODE_302_FOUND(302, "FOUND"),

        /**
         * Used a lot after receiving a post response.  The pattern is to
         * receive the post, then redirect to a new page. See <a href="https://en.wikipedia.org/wiki/Post/Redirect/Get">...</a>
         */
        CODE_303_SEE_OTHER(303, "SEE OTHER"),
        CODE_304_NOT_MODIFIED(304, "NOT MODIFIED"),
        CODE_307_TEMPORARY_REDIRECT(307, "TEMPORARY REDIRECT"),
        CODE_308_PERMANENT_REDIRECT(308, "PERMANENT REDIRECT"),

        /* Client error responses (400 – 499) */

        CODE_400_BAD_REQUEST(400, "BAD REQUEST"),
        CODE_401_UNAUTHORIZED(401, "UNAUTHORIZED"),
        CODE_403_FORBIDDEN(403, "FORBIDDEN"),
        CODE_404_NOT_FOUND(404, "NOT FOUND"),
        CODE_405_METHOD_NOT_ALLOWED(405, "METHOD NOT ALLOWED"),
        CODE_406_NOT_ACCEPTABLE(406, "NOT ACCEPTABLE"),
        CODE_408_REQUEST_TIMEOUT(408, "REQUEST TIMEOUT"),
        CODE_409_CONFLICT(409, "CONFLICT"),
        CODE_410_GONE(410, "GONE"),
        CODE_411_LENGTH_REQUIRED(411, "LENGTH REQUIRED"),
        CODE_413_PAYLOAD_TOO_LARGE(413, "PAYLOAD TOO LARGE"),
        CODE_414_URI_TOO_LONG(414, "URI TOO LONG"),
        CODE_415_UNSUPPORTED_MEDIA_TYPE(415, "UNSUPPORTED MEDIA TYPE"),
        CODE_426_UPGRADE_REQUIRED(426, "UPGRADE REQUIRED"),
        CODE_429_TOO_MANY_REQUESTS(429, "TOO MANY REQUESTS"),
        CODE_431_REQUEST_HEADER_FIELDS_TOO_LARGE(431, "REQUEST HEADER FIELDS TOO LARGE"),

        /* Server error responses (500 – 599) */

        CODE_500_INTERNAL_SERVER_ERROR(500, "INTERNAL SERVER ERROR"),
        CODE_501_NOT_IMPLEMENTED(501, "NOT IMPLEMENTED"),
        CODE_502_BAD_GATEWAY(502, "BAD GATEWAY"),
        CODE_503_SERVICE_UNAVAILABLE(503, "SERVICE UNAVAILABLE"),
        CODE_504_GATEWAY_TIMEOUT(504, "GATEWAY TIMEOUT"),
        CODE_505_HTTP_VERSION_NOT_SUPPORTED(505, "HTTP VERSION NOT SUPPORTED"),

        /**
         * The null object, meant to represent "no status code"
         */
        NULL(0, "NULL OBJECT")
        ;

        public final int code;
        public final String shortDescription;

        StatusCode(int code, String shortDescription) {
            this.code = code;
            this.shortDescription = shortDescription;
        }

        static StatusCode findByCode(int code) {
            return Arrays.stream(StatusCode.values())
                    .filter(x -> x.code == code)
                    .findFirst()
                    .orElseThrow();
        }
    }

    /**
     * Parses a string value of a status line from an HTTP
     * server.  If the input value is null or empty, we'll
     * return a {@link StatusLine} with null-object values
     */
    public static StatusLine extractStatusLine(String value) {
        if (value == null || value.isBlank()) {
            return StatusLine.EMPTY;
        }
        Matcher mr = StatusLine.statusLineRegex.matcher(value);
        mustBeTrue(mr.matches(), String.format("%s must match the statusLinePattern: %s", value, statusLinePattern));
        String version = mr.group(1);
        HttpVersion httpVersion = switch (version) {
            case "1.1" -> HttpVersion.ONE_DOT_ONE;
            case "1.0" -> HttpVersion.ONE_DOT_ZERO;
            default -> throw new WebServerException(String.format("HTTP version was not an acceptable value. Given: %s", version));
        };
        StatusCode status = StatusCode.findByCode(Integer.parseInt(mr.group(2)));

        return new StatusLine(status, httpVersion, value);
    }
}
