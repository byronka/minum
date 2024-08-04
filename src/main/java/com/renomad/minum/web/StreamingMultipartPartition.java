package com.renomad.minum.web;


import com.renomad.minum.utils.RingBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

/**
 * This class represents a single partition in a multipart/form
 * Request body, when read as an InputStream.  This enables the
 * developer to pull data incrementally, rather than reading it
 * all into memory at once.
 */
public class StreamingMultipartPartition extends InputStream {

    private final Headers headers;
    private final InputStream inputStream;
    private final ContentDisposition contentDisposition;
    private final int contentLength;
    /**
     * After we hit the boundary, we will set this flag to true, and all
     * subsequent reads will return -1.
     */
    private boolean isFinished = false;

    /**
     * This buffer follows along with what we are reading, so we can
     * easily compare against our boundary value.  There are four extra
     * bytes included, since multipart splits the content by two
     * dashes, followed by the boundary value, and then two dashes afterwards
     * on the last boundary.
     * <pre>
     * That is,
     * for a typical boundary:
     *
     *   --boundary_value
     *
     * and for the last boundary:
     *
     *   --boundary_value--
     *</pre>
     */
    private final RingBuffer<Byte> recentBytesBuffer;
    private final CountBytesRead countBytesRead;
    private final List<Byte> boundaryValueList;
    private boolean hasFilledBuffer;

    StreamingMultipartPartition(Headers headers,
                                       InputStream inputStream,
                                       ContentDisposition contentDisposition,
                                       String boundaryValue,
                                       CountBytesRead countBytesRead,
                                       int contentLength) {

        this.headers = headers;
        this.inputStream = inputStream;
        this.contentDisposition = contentDisposition;
        this.contentLength = contentLength;
        String boundaryValue1 = "\r\n--" + boundaryValue;
        byte[] bytes = boundaryValue1.getBytes(StandardCharsets.US_ASCII);
        boundaryValueList = IntStream.range(0, bytes.length).mapToObj(i -> bytes[i]).toList();

        /*
         * To explain the numbers here: we add one at the beginning to represent
         * the single character at the far left that is what we will actually return.
         * We have to fill the cache before we start sending anything.  The number
         * at the end represents the extra characters of the boundary - dashes,
         * carriage return, newline.
         */
        recentBytesBuffer = new RingBuffer<>(boundaryValue1.length(), Byte.class);
        this.countBytesRead = countBytesRead;
    }

    public Headers getHeaders() {
        return headers;
    }

    public ContentDisposition getContentDisposition() {
        return contentDisposition;
    }


    /**
     * Reads from the inputstream using a buffer for checking whether we've
     * hit the end of a multpart partition.
     * @return -1 if we're at the end of a partition
     * @throws IOException if the inputstream is closed unexpectedly while reading.
     */
    @Override
    public int read() throws IOException {
        if (isFinished) {
            return -1;
        }
        if (!hasFilledBuffer) {
            fillBuffer();
            boolean atTheEnd = recentBytesBuffer.containsAt(boundaryValueList, 0);
            if (atTheEnd) {
                // don't really do anything with this, it's just to collect the
                // last characters to have a clean finish.
                byte[] unused = inputStream.readNBytes(2);
                isFinished = true;
                return -1;
            }
        } else {
            int result = inputStream.read();
            countBytesRead.increment();
            if (countBytesRead.getCount() >= contentLength) {
                isFinished = true;
                return -1;
            }
            if (result == -1) {
                throw new IOException("Error: The inputstream has closed unexpectedly while reading");
            }
            byte byteValue = (byte) result;
            boolean isAtEndOfPartition = updateRecentBytesBufferAndCheck(byteValue);
            if (isAtEndOfPartition) {
                // don't really do anything with this, it's just to collect the
                // last characters to have a clean finish.
                byte[] unused = inputStream.readNBytes(2);
                isFinished = true;
                return -1;
            }

        }
        return ((int)recentBytesBuffer.atNextIndex()) & 0xff;
    }

    private void fillBuffer() throws IOException {
        for (int i = 0; i < recentBytesBuffer.getLimit(); i++) {
            int result = inputStream.read();
            countBytesRead.increment();
            if (result == -1) {
                throw new IOException("Error: The inputstream has closed unexpectedly while reading");
            }
            byte byteValue = (byte) result;
            updateRecentBytesBufferAndCheck(byteValue);
        }
        hasFilledBuffer = true;
    }

    @Override
    public byte[] readAllBytes()  {
        var baos = new ByteArrayOutputStream();
        while (true) {
            int result = 0;
            try {
                result = read();
            } catch (IOException e) {
                throw new WebServerException(e);
            }
            if (result == -1) {
                return baos.toByteArray();
            }
            baos.write((byte)result);
        }
    }

    /**
     * Updates the buffer with the last characters read, and returns
     * true if we have encountered the end of this partition.
     */
    private boolean updateRecentBytesBufferAndCheck(byte newByte) {
        recentBytesBuffer.add(newByte);
        return recentBytesBuffer.containsAt(boundaryValueList, 0);
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