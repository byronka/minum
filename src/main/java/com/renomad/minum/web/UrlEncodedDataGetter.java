package com.renomad.minum.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This class enables pulling key-value pairs one at a time
 * from the Request body. This enables the developer to pull data
 * incrementally, rather than reading it all into memory at once.
 */
public class UrlEncodedDataGetter extends InputStream {
    private final InputStream inputStream;
    private final CountBytesRead countBytesRead;
    private final long contentLength;
    /**
     * After we hit the boundary, we will set this flag to true, and all
     * subsequent reads will return -1.
     */
    private boolean isFinished = false;

    UrlEncodedDataGetter(InputStream inputStream, CountBytesRead countBytesRead, long contentLength) {
        this.inputStream = inputStream;
        this.countBytesRead = countBytesRead;
        this.contentLength = contentLength;
    }

    /**
     * Mostly similar behavior to {@link InputStream#read()}
     * @throws IOException if the inputstream is closed unexpectedly while reading.
     */
    @Override
    public int read() throws IOException {
        if (isFinished) {
            return -1;
        }
        if (countBytesRead.getCount() == contentLength) {
            isFinished = true;
            return -1;
        }
        int result = inputStream.read();

        if (result == -1) {
            isFinished = true;
            // I know this is surprising, however: Because we always have the content length while reading the body,
            // we know exactly when we expect to read the last byte.  If we read and get a -1, it means
            // the stream is closed - but that should not have happened, because we should have stopped reading when
            // we hit the limit of bytes to read.  But in the real world, it will happen:  You can observe it by uploading a large
            // file and using the browser's "stop" button during the upload.
            throw new IOException("Error: The inputstream has closed unexpectedly while reading");
        }

        countBytesRead.increment();
        char byteValue = (char) result;
        if (byteValue == '&') {
            isFinished = true;
            return -1;
        }
        return result;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        var baos = new ByteArrayOutputStream();
        while (true) {
            int result = read();
            if (result == -1) {
                // if our read function determines we are at the end of the value,
                // because we encountered an ampersand, it will return a -1 value,
                // and we return our value - but more keys and values expected.
                return baos.toByteArray();
            }
            baos.write((byte)result);
        }
    }

    /**
     * By "close", we will read from the {@link InputStream} until we have finished the body,
     * so that our InputStream has been read until the start of the next partition.
     */
    @Override
    public void close() throws IOException {
        while (true) {
            int result = read();
            if (result == -1) {
                return;
            }
        }
    }
}
