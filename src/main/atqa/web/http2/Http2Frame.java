package atqa.web.http2;

import atqa.web.Request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * All frames begin with a fixed 9-octet header followed by a variable-length frame payload.
 * See RFC 9113 section 4.1 - Frame format
 * <pre>
 * {@code
 * HTTP Frame {
 *   Length (24),
 *   Type (8),
 *
 *   Flags (8),
 *
 *   Reserved (1),
 *   Stream Identifier (31),
 *
 *   Frame Payload (..),
 * }
 * }
 * </pre>
 * See the fields for further details.
 */
public abstract class Http2Frame {
    private final int length;
    private final Http2FrameType type;
    private final byte flags;
    private final int streamIdentifier;
    private final byte[] payload;

    /**
     *
     */
    public Http2Frame(
            int length,
            Http2FrameType type,
            byte flags,
            int streamIdentifier,
            byte[] payload) {
        this.length = length;
        this.type = type;
        this.flags = flags;
        this.streamIdentifier = streamIdentifier;
        this.payload = payload;
    }

    /**
     * Length: The length of the frame payload expressed as an unsigned 24-bit
     * integer in units of octets. Values greater than 2^14 (16,384) MUST NOT
     * be sent unless the receiver has set a larger value for SETTINGS_MAX_FRAME_SIZE.
     * The 9 octets of the frame header are not included in this value.
     */
    public int length() {
        return length;
    }

    /**
     * Type: The 8-bit type of the frame. The frame type determines the
     * format and semantics of the frame. Frames defined in this document are
     * listed in Section 6. Implementations MUST ignore and discard frames of unknown types.
     */
    public Http2FrameType type() {
        return type;
    }

    /**
     * Flags: An 8-bit field reserved for boolean flags specific to the frame
     * type. Flags are assigned semantics specific to the indicated frame
     * type. Unused flags are those that have no defined semantics for a
     * particular frame type. Unused flags MUST be ignored on receipt and
     * MUST be left unset (0x00) when sending.
     */
    public byte flags() {
        return flags;
    }

    /**
     * Stream Identifier: A stream identifier (see Section 5.1.1) expressed
     * as an unsigned 31-bit integer. The value 0x00 is reserved for frames
     * that are associated with the connection as a whole as opposed to
     * an individual stream.
     */
    public int streamIdentifier() {
        return streamIdentifier;
    }

    /**
     * Given a {@link Request}, return a list of {@link Http2Frame}
     * making up the serialized set of data we'll put on the wire
     * @param scheme "http" or "https"
     * @param host the hostname of the server
     */
    public static List<Http2Frame> encode(Request r, String scheme, String host) {
        return List.of(Http2HeaderFrame.make(1, r.startLine(), r.headers(), scheme, host));
    }

    /**
     * Given an input stream of bytes, either returns a value type
     * of HTTP2 frame, or returns a null (or an exception if it's really broken)
     */
    public static Http2Frame decode(ByteArrayInputStream in) {
        // first three bytes are the length
        // Read the body size.
        int bodyPayloadSize = determinePayloadSize(in);
        byte type = (byte) in.read();
        byte flags = (byte) in.read();
        int streamIdentifier = determineStreamIdentifier(in);
        byte[] payload;
        try {
            payload = in.readNBytes(bodyPayloadSize);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Http2FrameType frameType = Http2FrameType.getByCode(type);
        switch (frameType) {
            case HEADERS -> {
                return new Http2HeaderFrame(
                        bodyPayloadSize,
                        flags,
                        streamIdentifier,
                        payload
                );
            }
        }
        return null;
    }

    private static int determinePayloadSize(ByteArrayInputStream in) {
        int bodyPayloadSize = 0;
        bodyPayloadSize |= (in.read() << 24);
        bodyPayloadSize |= (in.read() << 16);
        bodyPayloadSize |= (in.read() << 8);
        bodyPayloadSize |= in.read();

        int MAX_HTTP2_PAYLOAD_SIZE = 16_384;
        if (bodyPayloadSize > MAX_HTTP2_PAYLOAD_SIZE) {
            throw new RuntimeException("Header list too large");
        }

        return bodyPayloadSize;
    }

    private static int determineStreamIdentifier(ByteArrayInputStream in) {
        int streamIdentifier = 0;
        streamIdentifier |= (in.read() << 24);
        streamIdentifier |= (in.read() << 16);
        streamIdentifier |= (in.read() << 8);
        streamIdentifier |= in.read();

        if (streamIdentifier < 0) {
            throw new RuntimeException("Stream identifier must be an unsigned 31-bit integer");
        }
        return streamIdentifier;
    }

    /**
     * The structure and content of the frame payload are dependent
     * entirely on the frame type.
     */
    public byte[] payload() {
        return payload;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Http2Frame) obj;
        return this.length == that.length &&
                Objects.equals(this.type, that.type) &&
                this.flags == that.flags &&
                this.streamIdentifier == that.streamIdentifier &&
                Arrays.equals(this.payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(length, type, flags, streamIdentifier, Arrays.hashCode(payload));
    }

    @Override
    public String toString() {
        return "Http2Frame[" +
                "length=" + length + ", " +
                "type=" + type + ", " +
                "flags=" + flags + ", " +
                "streamIdentifier=" + streamIdentifier + ", " +
                "payload=" + payload + ']';
    }

    public byte[] toBytes() {
        var baos = new ByteArrayOutputStream();
        try {
            baos.write(ByteBuffer.allocate(4).putInt(length).array());
            baos.write(type.getTypeCode());
            baos.write(ByteBuffer.allocate(4).putInt(streamIdentifier).array());
            baos.write(payload);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return baos.toByteArray();
    }
}
