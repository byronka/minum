package com.renomad.minum.web;

import java.io.IOException;
import java.io.InputStream;

/**
 * An interface for the {@link InputStreamUtils} implementation.
 * Solely created to provide better testing access
 */
interface IInputStreamUtils {

    /**
     * Reads a line of text, stopping when reading a newline.
     * <br>
     * Skips over carriage returns, so we read a HTTP_CRLF properly.
     * <br>
     * <em>Note</em>: This is not a general-purpose line reader.  It is custom-designed
     * for the inner workings of the Minum web server.  If you have need of
     * a typical {@link InputStream} line reader, it is probably better to
     * use {@link java.io.BufferedReader}
     * @return     A String containing the contents of the line, not including
     *             any line-termination characters, or null if the end of the
     *             stream has been reached without reading any characters
     *
     * @throws     IOException  If an I/O error occurs
     */
    String readLine(InputStream inputStream) throws IOException;

    /**
     * Reads "lengthToRead" bytes from the input stream
     */
    byte[] read(int lengthToRead, InputStream inputStream);
}
