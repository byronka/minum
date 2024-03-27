package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.utils.UtilsException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static com.renomad.minum.utils.Invariants.*;
import static com.renomad.minum.utils.ByteUtils.byteListToArray;

/**
 * Handy helpful utilities for working with input streams.
 */
public final class InputStreamUtils implements IInputStreamUtils {

    private final Constants constants;


    public InputStreamUtils(Constants constants) {
        this.constants = constants;
    }

    @Override
    public byte[] readUntilEOF(InputStream inputStream) {
        final var result = new ArrayList<Byte>();
        try {
            for (int i = 0; ; i++) {
                if (i >= constants.maxReadSizeBytes) {
                    inputStream.close();
                    throw new ForbiddenUseException("client sent more bytes than allowed.  Current max: " + constants.maxReadSizeBytes);
                }
                int a = inputStream.read();
                if (a == -1) return byteListToArray(result);

                result.add((byte) a);
            }
        } catch (IOException ex) {
            throw new UtilsException(ex);
        }
    }

    @Override
    public byte[] readChunkedEncoding(InputStream inputStream) {
        final var result = new ByteArrayOutputStream( );
        try {
            while (true) {
                String countToReadString = readLine(inputStream);
                if (countToReadString.isEmpty()) {
                    return new byte[0];
                }
                mustNotBeNull(countToReadString);
                int countToRead = Integer.parseInt(countToReadString, 16);

                result.write(read(countToRead, inputStream));

                readLine(inputStream);
                if (countToRead == 0) {
                    readLine(inputStream);
                    break;
                }

            }
        } catch (IOException ex) {
            throw new UtilsException(ex);
        }
        return result.toByteArray();
    }

    @Override
    public String readLine(InputStream inputStream) throws IOException  {
        final int NEWLINE_DECIMAL = 10;
        final int CARRIAGE_RETURN_DECIMAL = 13;

        final var result = new ByteArrayOutputStream(constants.maxReadLineSizeBytes / 3);
        for (int i = 0;; i++) {
            if (i >= constants.maxReadLineSizeBytes) {
                inputStream.close();
                throw new ForbiddenUseException("client sent more bytes than allowed for a single line.  Current max: " + constants.maxReadLineSizeBytes);
            }
            int a = inputStream.read();
            if (a == -1) return result.toString(StandardCharsets.UTF_8);
            if (a == CARRIAGE_RETURN_DECIMAL) continue;
            if (a == NEWLINE_DECIMAL) break;
            result.write(a);
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] read(int lengthToRead, InputStream inputStream) {
        if (lengthToRead >= constants.maxReadSizeBytes) {
            throw new ForbiddenUseException("client requested to send more bytes than allowed.  Current max: " + constants.maxReadSizeBytes + " asked to receive: " + lengthToRead);
        }
        final int typicalBufferSize = 1024 * 8;
        byte[] buf = new byte[Math.min(lengthToRead, typicalBufferSize)]; // 8k buffer is my understanding of a decent size.  Fast, doesn't waste too much space.
        byte[] data;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read;
        int totalRead = 0;
        try {
            while ((read = inputStream.read(buf)) >= 0) {
                totalRead += read;
                if (totalRead < lengthToRead) {
                    // if we haven't gotten everything we wanted, write this to the output and loop again
                    baos.write(buf, 0, read);
                } else {
                    baos.write(buf, 0, read - (totalRead - lengthToRead));
                    break;
                }
            }
        } catch (IOException ex) {
            throw new UtilsException(ex);
        }
        data = baos.toByteArray();

        if (data.length != lengthToRead) {
            String message = String.format("length of bytes read (%d) must be what we expected (%d)", data.length, lengthToRead);
            throw new ForbiddenUseException(message);
        }
        return data;
    }
}
