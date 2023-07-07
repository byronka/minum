package minum.web.http2;

import minum.web.Headers;
import minum.web.StartLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * From RFC 9113:
 * The HEADERS frame (type=0x01) is used to open a stream (Section 5.1),
 * and additionally carries a field block fragment. Despite the name, a
 * HEADERS frame can carry a header section or a trailer section. HEADERS
 * frames can be sent on a stream in the "idle", "reserved (local)",
 * "open", or "half-closed (remote)" state.
 */
public class Http2HeaderFrame extends Http2Frame {


    /**
     * <pre>
       6.1.  Indexed Header Field Representation

       An indexed header field representation identifies an entry in either
       the static table or the dynamic table (see Section 2.3).

       An indexed header field representation causes a header field to be
       added to the decoded header list, as described in Section 3.2.

         0   1   2   3   4   5   6   7
       +---+---+---+---+---+---+---+---+
       | 1 |        Index (7+)         |
       +---+---------------------------+

                          Figure 5: Indexed Header Field

       An indexed header field starts with the '1' 1-bit pattern, followed
       by the index of the matching header field, represented as an integer
       with a 7-bit prefix (see Section 5.1).

       The index value of 0 is not used.  It MUST be treated as a decoding
       error if found in an indexed header field representation.
     </pre>
     */
    final static byte INDEXED_HEADER_FIELD_REPRESENTATION = (byte) 0b10_00_00_00;

    /**
     * <pre>
    6.2.1.  Literal Header Field with Incremental Indexing

    A literal header field with incremental indexing representation
    results in appending a header field to the decoded header list and
    inserting it as a new entry into the dynamic table.

              0   1   2   3   4   5   6   7
            +---+---+---+---+---+---+---+---+
            | 0 | 1 |      Index (6+)       |
            +---+---+-----------------------+
            | H |     Value Length (7+)     |
            +---+---------------------------+
            | Value String (Length octets)  |
            +-------------------------------+

     Figure 6: Literal Header Field with Incremental Indexing -- Indexed


              0   1   2   3   4   5   6   7
            +---+---+---+---+---+---+---+---+
            | 0 | 1 |           0           |
            +---+---+-----------------------+
            | H |     Name Length (7+)      |
            +---+---------------------------+
            |  Name String (Length octets)  |
            +---+---------------------------+
            | H |     Value Length (7+)     |
            +---+---------------------------+
            | Value String (Length octets)  |
            +-------------------------------+

     Figure 7: Literal Header Field with Incremental Indexing -- New Name

    A literal header field with incremental indexing representation
    starts with the '01' 2-bit pattern.

    If the header field name matches the header field name of an entry
    stored in the static table or the dynamic table, the header field
    name can be represented using the index of that entry.  In this case,
    the index of the entry is represented as an integer with a 6-bit
    prefix (see Section 5.1).  This value is always non-zero.

    Otherwise, the header field name is represented as a string literal
    (see Section 5.2).  A value 0 is used in place of the 6-bit index,
    followed by the header field name.

    Either form of header field name representation is followed by the
    header field value represented as a string literal (see Section 5.2).
     </pre>
    */
    final static byte LITERAL_WITH_INDEXING_HEADER_FIELD_REPRESENTATION = (byte) 0b01_00_00_00;

    /**
     * Don't use this to build a HEADER frame - use the static method
     * at {@link #make(int, StartLine, Headers, String, String)} instead.  This is public
     * so that it can be used from tests.
     */
    public Http2HeaderFrame(int length, byte flags, int streamIdentifier, byte[] payload) {
        super(length, Http2FrameType.HEADERS, flags, streamIdentifier, payload);
    }

    /**
     * Build an Http2 Header frame. This should be the commonly-used
     * method for prod use.
     */
    public static Http2HeaderFrame make(int streamIdentifier, StartLine sl, Headers headers, String scheme, String host) {
        var fieldBlockFragment = encodeDataForHeadersPayload(sl, headers, scheme, host);
        // The following line should suffice for our typical use cases.
        var flags = makeHeaderFlags(false, false, true, true);
        return new Http2HeaderFrame(
                fieldBlockFragment.length,
                flags,
                streamIdentifier,
                fieldBlockFragment);
    }


    /**
     * Set flags for a HEADER frame
     * @param isPriority When set, the PRIORITY flag indicates that the Exclusive, Stream Dependency, and Weight fields are present.
     * @param isPadded When set, the PADDED flag indicates that the Pad Length field and any padding that it describes are present.
     * @param endHeaders When set, the END_HEADERS flag indicates that this frame contains an entire field block (Section 4.3) and is not followed by any CONTINUATION frames.
     * @param endStream When set, the END_STREAM flag indicates that the field block (Section 4.3) is the last that the endpoint will send for the identified stream.
     */
    private static byte makeHeaderFlags(boolean isPriority, boolean isPadded, boolean endHeaders, boolean endStream) {
        byte result = 0;
        if (isPriority) result |= (byte) 0x20;
        if (isPadded) result |= (byte) 0x208;
        if (endHeaders) result |= (byte) 0x04;
        if (endStream) result |= (byte) 0x01;

        return result;
    }

    /**
     * Convert the data in {@link StartLine} and {@link Headers} into
     * suitable format for inclusion in the HEADERS frame for HTTP/2
     * <br>
     * @param scheme http or https
     * @param host converted to two headers - authority and host
     * @see HeaderFields#headersStaticTable
     */
    private static byte[] encodeDataForHeadersPayload(StartLine startLine, Headers headers, String scheme, String host) {
        final var encodedFieldLines = new ByteArrayOutputStream();

        encodeVerb(startLine.getVerb(), encodedFieldLines);
        encodePath(startLine.getPathDetails().isolatedPath(), encodedFieldLines);
        encodeScheme(scheme, encodedFieldLines);
        encodeAuthority(host, encodedFieldLines);
        encodeRestOfHeaders(headers, encodedFieldLines);

        return encodedFieldLines.toByteArray();
    }

    private static void encodeRestOfHeaders(Headers headers, ByteArrayOutputStream encodedFieldLines) {
        // loop through the rest of the headers
        for (var h : headers.getHeadersMap().entrySet()) {
            int i = HeaderFields.headersStaticTable.indexOf(
                    new HeaderFields.HeaderField(h.getKey(), ""));
            // if we found that header in our static table, use it
            if (i >= 0) {
                encodedFieldLines.write(LITERAL_WITH_INDEXING_HEADER_FIELD_REPRESENTATION | i);
                String joinedValues = String.join(";", h.getValue());
                encodedFieldLines.write(joinedValues.length()); //whoa nelly
                try {
                    encodedFieldLines.write(joinedValues.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        }
    }

    /**
     * See {@link HeaderFields#headersStaticTable} for the indexes I am using
     */
    private static void encodeAuthority(String host, ByteArrayOutputStream encodedFieldLines) {
        // write the authority
        encodedFieldLines.write(LITERAL_WITH_INDEXING_HEADER_FIELD_REPRESENTATION | 1);
        encodedFieldLines.write(host.length());
        try {
            encodedFieldLines.write(host.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * See {@link HeaderFields#headersStaticTable} for the indexes I am using
     */
    private static void encodeScheme(String scheme, ByteArrayOutputStream encodedFieldLines) {
        // convert the scheme
        if (scheme.equals("http")) {
            encodedFieldLines.write(INDEXED_HEADER_FIELD_REPRESENTATION | 0x6);
        } else if (scheme.equals("https")) {
            encodedFieldLines.write(INDEXED_HEADER_FIELD_REPRESENTATION | 0x7);
        }
    }

    /**
     * See {@link HeaderFields#headersStaticTable} for the indexes I am using
     */
    private static void encodePath(String path, ByteArrayOutputStream encodedFieldLines) {
        // convert the path
        if (path.equals("/")) {
            encodedFieldLines.write(INDEXED_HEADER_FIELD_REPRESENTATION | 0x4);
        } else if (path.equals("/index.html")) {
            encodedFieldLines.write(INDEXED_HEADER_FIELD_REPRESENTATION | 0x5);
        } else {
            encodedFieldLines.write(LITERAL_WITH_INDEXING_HEADER_FIELD_REPRESENTATION | 4);
            encodedFieldLines.write(path.length());
            try {
                encodedFieldLines.write(path.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * See {@link HeaderFields#headersStaticTable} for the indexes I am using
     */
    private static void encodeVerb(StartLine.Verb verb, ByteArrayOutputStream encodedFieldLines) {
        // convert the verb
        if (verb.equals(StartLine.Verb.GET)) {
            encodedFieldLines.write(INDEXED_HEADER_FIELD_REPRESENTATION | 0x2);
        } else if (verb.equals(StartLine.Verb.POST)) {
            encodedFieldLines.write(INDEXED_HEADER_FIELD_REPRESENTATION | 0x3);
        }
    }

}
