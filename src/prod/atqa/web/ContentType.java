package atqa.web;

/**
 * These are mime types (see https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types)
 * which we'll use when conversing with clients to describe data
 */
public enum ContentType {
    // Text MIME types - see https://www.iana.org/assignments/media-types/media-types.xhtml#text
    TEXT_HTML("Content-Type: text/html; charset=UTF-8"),
    TEXT_CSS("Content-Type: text/css"),

    // Application MIME types - see https://www.iana.org/assignments/media-types/media-types.xhtml#application
    APPLICATION_JAVASCRIPT("Content-Type: application/javascript"),
    IMAGE_WEBP("Content-Type: image/webp");

    public final String headerString;

    ContentType(String headerString) {
        this.headerString = headerString;
    }
}
