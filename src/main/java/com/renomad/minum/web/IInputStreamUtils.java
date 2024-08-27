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
