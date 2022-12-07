package atqa.web;

/**
 * These are mime types (see docs/mime_types.txt)
 * which we'll use when conversing with clients to describe data
 */
public enum ContentType {
    // Text MIME types - see https://www.iana.org/assignments/media-types/media-types.xhtml#text
    TEXT_HTML("Content-Type: text/html; charset=UTF-8"),
    TEXT_CSS("Content-Type: text/css"),

    // Application MIME types - see https://www.iana.org/assignments/media-types/media-types.xhtml#application
    APPLICATION_JAVASCRIPT("Content-Type: application/javascript"),
    IMAGE_WEBP("Content-Type: image/webp"),

    /**
     * Used when we aren't returning any body to the client.  e.g. 204, 303 statuses.
     */
    NONE("");

    public final String headerString;

    ContentType(String headerString) {
        this.headerString = headerString;
    }
}
