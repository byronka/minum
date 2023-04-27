package atqa.web.http2;

import java.util.*;

/**
 * This class holds the static and dynamic header field tables.
 * <br>
 * The static table is instantiated in this class, and the
 * dynamic table is a first-in first-out data structure
 */
public class HeaderFields {

    private final Deque<HeaderField> headersDynamicTable;

    public HeaderFields() {
        headersDynamicTable = new ArrayDeque<>();
    }


    /**
     * These are alternately known as "Header Fields" (RFC 7541 )or
     * "Field Lines" (RFC 9113)
     */
    public record HeaderField(String name, String value) {}

    /**
     * These headers are defined in Appendix A of RFC 7541, HPACK
     * compression for headers
     *
     * <pre>
     *     Appendix A.  Static Table Definition
     *
     *    The static table (see Section 2.3.1) consists in a predefined and
     *    unchangeable list of header fields.
     *
     *    The static table was created from the most frequent header fields
     *    used by popular web sites, with the addition of HTTP/2-specific
     *    pseudo-header fields (see Section 8.1.2.1 of [HTTP2]).  For header
     *    fields with a few frequent values, an entry was added for each of
     *    these frequent values.  For other header fields, an entry was added
     *    with an empty value.
     *
     *    Table 1 lists the predefined header fields that make up the static
     *    table and gives the index of each entry.
     * </pre>
     */
    public static final List<HeaderField> headersStaticTable = List.of(
      /* NOT USED */  new HeaderField("", ""),
      /* 1 */   new HeaderField(":authority", ""),
      /* 2 */   new HeaderField(":method", "GET"),
      /* 3 */   new HeaderField(":method", "POST"),
      /* 4 */   new HeaderField(":path", "/"),
      /* 5 */   new HeaderField(":path", "/index.html"),
      /* 6 */   new HeaderField(":scheme", "http"),
      /* 7 */   new HeaderField(":scheme", "https"),
      /* 8 */   new HeaderField(":status", "200"),
      /* 9 */   new HeaderField(":status", "204"),
      /* 10 */   new HeaderField(":status", "206"),
      /* 11 */   new HeaderField(":status", "304"),
      /* 12 */   new HeaderField(":status", "400"),
      /* 13 */   new HeaderField(":status", "404"),
      /* 14 */   new HeaderField(":status", "500"),
      /* 15 */   new HeaderField("accept-charset", ""),
      /* 16 */   new HeaderField("accept-encoding", "gzip, deflate"),
      /* 17 */   new HeaderField("accept-language", ""),
      /* 18 */   new HeaderField("accept-ranges", ""),
      /* 19 */   new HeaderField("accept", ""),
      /* 20 */   new HeaderField("access-control-allow-origin", ""),
      /* 21 */   new HeaderField("age", ""),
      /* 22 */   new HeaderField("allow", ""),
      /* 23 */   new HeaderField("authorization", ""),
      /* 24 */   new HeaderField("cache-control", ""),
      /* 25 */   new HeaderField("content-disposition", ""),
      /* 26 */   new HeaderField("content-encoding", ""),
      /* 27 */   new HeaderField("content-language", ""),
      /* 28 */   new HeaderField("content-length", ""),
      /* 29 */   new HeaderField("content-location", ""),
      /* 30 */   new HeaderField("content-range", ""),
      /* 31 */   new HeaderField("content-type", ""),
      /* 32 */   new HeaderField("cookie", ""),
      /* 33 */   new HeaderField("date", ""),
      /* 34 */   new HeaderField("etag", ""),
      /* 35 */   new HeaderField("expect", ""),
      /* 36 */   new HeaderField("expires", ""),
      /* 37 */   new HeaderField("from", ""),
      /* 38 */   new HeaderField("host", ""),
      /* 39 */   new HeaderField("if-match", ""),
      /* 40 */   new HeaderField("if-modified-since", ""),
      /* 41 */   new HeaderField("if-none-match", ""),
      /* 42 */   new HeaderField("if-range", ""),
      /* 43 */   new HeaderField("if-unmodified-since", ""),
      /* 44 */   new HeaderField("last-modified", ""),
      /* 45 */   new HeaderField("link", ""),
      /* 46 */   new HeaderField("location", ""),
      /* 47 */   new HeaderField("max-forwards", ""),
      /* 48 */   new HeaderField("proxy-authenticate", ""),
      /* 49 */   new HeaderField("proxy-authorization", ""),
      /* 50 */   new HeaderField("range", ""),
      /* 51 */   new HeaderField("referer", ""),
      /* 52 */   new HeaderField("refresh", ""),
      /* 53 */   new HeaderField("retry-after", ""),
      /* 54 */   new HeaderField("server", ""),
      /* 55 */   new HeaderField("set-cookie", ""),
      /* 56 */   new HeaderField("strict-transport-security", ""),
      /* 57 */   new HeaderField("transfer-encoding", ""),
      /* 58 */   new HeaderField("user-agent", ""),
      /* 59 */   new HeaderField("vary", ""),
      /* 60 */   new HeaderField("via", ""),
      /* 61 */   new HeaderField("www-authenticate", "")
    );

}
