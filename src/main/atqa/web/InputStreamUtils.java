package atqa.web;

import atqa.utils.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static atqa.utils.Invariants.*;
import static atqa.web.ByteUtils.byteListToArray;

public class InputStreamUtils {

    /**
     * Read from the socket until it returns an EOF indicator (that is, -1)
     * Note: this *will block* until it gets to that EOF.
     */
    public static byte[] readUntilEOF(InputStream inputStream) throws IOException {
        final var result = new ArrayList<Byte>();
        while (true) {
            int a = inputStream.read();
            if (a == -1) {
                return byteListToArray(result);
            }

            result.add((byte) a);
        }
    }

    /**
     * reads following the algorithm for transfer-encoding: chunked.
     * See <a href="https://en.wikipedia.org/wiki/Chunked_transfer_encoding">chunked transfer encoding</a>
     */
    public static byte[] readChunkedEncoding(InputStream inputStream) throws IOException {
        final var result = new ByteArrayOutputStream( );
        while (true) {
            String countToReadString = readLine(inputStream);
            mustNotBeNull(countToReadString);
            int countToRead = Integer.parseInt(countToReadString, 16);

            result.write(read(countToRead, inputStream));
            readLine(inputStream);
            if (countToRead == 0) {
                readLine(inputStream);
                break;
            }

        }
        return result.toByteArray();
    }

    /**
     * Reads a line of text, stopping when reading a newline.
     * Skips over carriage returns, so we read a HTTP_CRLF properly.
     * <br>
     * If the stream ends, return null
     */
    public static String readLine(InputStream inputStream) throws IOException  {
        final int NEWLINE_DECIMAL = 10;
        final int CARRIAGE_RETURN_DECIMAL = 13;

        final var result = new ArrayList<Byte>();
        while (true) {
            int a = inputStream.read();

            if (a == -1) return null;

            if (a == CARRIAGE_RETURN_DECIMAL) {
                continue;
            }

            if (a == NEWLINE_DECIMAL) break;

            result.add((byte) a);

        }
        return StringUtils.byteListToString(result);
    }


    /**
     * Reads "length" bytes from the input stream
     */
    public static byte[] read(int length, InputStream inputStream) throws IOException {
        final var buf = new byte[length];
        final var lengthRead = inputStream.read(buf);
        mustBeFalse(lengthRead == -1, "end of file hit");
        mustBeFalse(lengthRead != length, String.format("length of bytes read (%d) wasn't equal to what we specified (%d)", lengthRead, length));
        return buf;
    }
}
