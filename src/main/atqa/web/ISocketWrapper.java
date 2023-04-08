package atqa.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;

public interface ISocketWrapper extends AutoCloseable {
    void send(String msg) throws IOException;

    void send(byte[] bodyContents) throws IOException;

    void sendHttpLine(String msg) throws IOException;

    /**
     * Reads a line of text, stopping when reading a newline.
     * Skips over carriage returns, so we read a HTTP_CRLF properly.
     * <br>
     * If the stream ends, return null
     */
    String readLine() throws IOException;

    String getLocalAddr();

    int getLocalPort();

    SocketAddress getRemoteAddr();

    void close() throws IOException;

    /**
     * Reads "length" bytes from the input stream
     */
    byte[] read(int length) throws IOException;

    /**
     * Read from the socket until it returns an EOF indicator (that is, -1)
     * Note: this *will block* until it gets to that EOF.
     */
    byte[] readUntilEOF() throws IOException;

    /**
     * reads following the algorithm for transfer-encoding: chunked.
     * See https://en.wikipedia.org/wiki/Chunked_transfer_encoding
     */
    byte[] readChunkedEncoding() throws IOException;

    /**
     * Returns this socket's input stream for more granular access
     */
    InputStream getInputStream();
}
