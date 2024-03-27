package com.renomad.minum.web;

import java.io.IOException;
import java.io.InputStream;

public interface IInputStreamUtils {

    /**
     * Read from the socket until it returns an EOF indicator (that is, -1)
     * Note: this *will block* until it gets to that EOF.
     */
    byte[] readUntilEOF(InputStream inputStream);

    /**
     * reads following the algorithm for transfer-encoding: chunked.
     * See <a href="https://en.wikipedia.org/wiki/Chunked_transfer_encoding">chunked transfer encoding</a>
     */
    byte[] readChunkedEncoding(InputStream inputStream);

    /**
     * Reads a line of text, stopping when reading a newline.
     * Skips over carriage returns, so we read a HTTP_CRLF properly.
     * <br>
     * If the stream ends, return what we have so far.
     */
    String readLine(InputStream inputStream) throws IOException;

    /**
     * Reads "lengthToRead" bytes from the input stream
     */
    byte[] read(int lengthToRead, InputStream inputStream);
}
