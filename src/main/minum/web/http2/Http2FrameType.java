package minum.web.http2;

/**
 * See Section 6 in RFC 9113
 */
public enum Http2FrameType {
    /**
     * DATA frames (type=0x00) convey arbitrary, variable-length
     * sequences of octets associated with a stream. One or more
     * DATA frames are used, for instance, to carry HTTP request
     * or response message contents.
     */
    DATA((byte) 0x00),


    /**
     * The HEADERS frame (type=0x01) is used to open a stream (Section 5.1),
     * and additionally carries a field block fragment. Despite the name, a
     * HEADERS frame can carry a header section or a trailer section. HEADERS
     * frames can be sent on a stream in the "idle", "reserved (local)",
     * "open", or "half-closed (remote)" state.
     */
    HEADERS((byte) 0x01),


    /**
     * The PRIORITY frame (type=0x02) is deprecated; see Section 5.3.2.
     * A PRIORITY frame can be sent in any stream state, including
     * idle or closed streams.
     */
    PRIORITY((byte) 0x02),

    /**
     * The RST_STREAM frame (type=0x03) allows for immediate
     * termination of a stream. RST_STREAM is sent to request
     * cancellation of a stream or to indicate that an error
     * condition has occurred.
     */
    RST_STREAM((byte) 0x03),


    /**
     * The SETTINGS frame (type=0x04) conveys configuration parameters
     * that affect how endpoints communicate, such as preferences and
     * constraints on peer behavior. The SETTINGS frame is also used to
     * acknowledge the receipt of those settings. Individually, a
     * configuration parameter from a SETTINGS frame is referred to
     * as a "setting".
     */
    SETTINGS((byte) 0x04),


    /**
     * The PUSH_PROMISE frame (type=0x05) is used to notify the peer endpoint
     * in advance of streams the sender intends to initiate. The PUSH_PROMISE
     * frame includes the unsigned 31-bit identifier of the stream the
     * endpoint plans to create along with a field section that provides
     * additional context for the stream. Section 8.4 contains a thorough
     * description of the use of PUSH_PROMISE frames.
     */
    PUSH_PROMISE((byte) 0x05),

    /**
     * The PING frame (type=0x06) is a mechanism for measuring a minimal
     * round-trip time from the sender, as well as determining whether
     * an idle connection is still functional. PING frames can be sent from any endpoint.
     */
    PING((byte) 0x06),

    /**
     * The GOAWAY frame (type=0x07) is used to initiate shutdown of a
     * connection or to signal serious error conditions. GOAWAY allows
     * an endpoint to gracefully stop accepting new streams while still
     * finishing processing of previously established streams. This enables
     * administrative actions, like server maintenance.
     */
    GOAWAY((byte) 0x07),

    /**
     * The WINDOW_UPDATE frame (type=0x08) is used to implement
     * flow control; see Section 5.2 for an overview.
     */
    WINDOW_UPDATE((byte) 0x08),

    /**
     * The CONTINUATION frame (type=0x09) is used to continue a
     * sequence of field block fragments (Section 4.3). Any number
     * of CONTINUATION frames can be sent, as long as the preceding
     * frame is on the same stream and is a HEADERS, PUSH_PROMISE,
     * or CONTINUATION frame without the END_HEADERS flag set.
     */
    CONTINUATION((byte) 0x09)

    ;

    private final byte typeCode;

    Http2FrameType(byte typeCode) {
        this.typeCode = typeCode;
    }

    public byte getTypeCode() {
        return typeCode;
    }

    public static Http2FrameType getByCode(byte typeCode) {
        for (Http2FrameType ht : Http2FrameType.values()) {
            if (ht.typeCode == typeCode) {
                return ht;
            }
        }
        throw new RuntimeException("no http2 frame type found for " + typeCode);
    }
}
