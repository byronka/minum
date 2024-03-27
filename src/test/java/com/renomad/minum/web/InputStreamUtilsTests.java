package com.renomad.minum.web;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.TestLogger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

public class InputStreamUtilsTests {

    private IInputStreamUtils inputStreamUtils;
    private TestLogger testLogger;
    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("input stream utils tests");
        testLogger = (TestLogger) context.getLogger();
        Properties properties = new Properties();
        properties.setProperty("MAX_READ_SIZE_BYTES", "3");
        inputStreamUtils = new InputStreamUtils(new Constants(properties));
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * If we send too many bytes to get read, an exception gets thrown
     */
    @Test
    public void testReadUntilEOF_EdgeCase_TooManyBytes() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));

        var exception = assertThrows(ForbiddenUseException.class, () -> inputStreamUtils.readUntilEOF(inputStream));

        assertEquals(exception.getMessage(), "client sent more bytes than allowed.  Current max: 3");
    }

    /**
     * If an IOException gets thrown, it will be converted to a RuntimeException
     */
    @Test
    public void testReadUntilEOF_EdgeCase_IOException() {
        InputStream inputStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("test exception only, no worries");
            }
        };

        var exception = assertThrows(RuntimeException.class, () -> inputStreamUtils.readUntilEOF(inputStream));

        assertEquals(exception.getMessage(), "java.io.IOException: test exception only, no worries");
    }

    /**
     * If we send too many bytes to get read, an exception gets thrown
     */
    @Test
    public void testReadChunkedEncoding_EdgeCase_TooManyBytes() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("3\nabc".getBytes(StandardCharsets.UTF_8));

        var exception = assertThrows(ForbiddenUseException.class, () -> inputStreamUtils.readChunkedEncoding(inputStream));

        assertEquals(exception.getMessage(), "client requested to send more bytes than allowed.  Current max: 3 asked to receive: 3");
    }

    /**
     * If there is nothing sent for the count of bytes to read, we'll get an empty byte array
     * from this method.
     */
    @Test
    public void testReadChunkedEncoding_EdgeCase_NullCount() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

        byte[] result = inputStreamUtils.readChunkedEncoding(inputStream);

        assertEqualByteArray(result, new byte[0]);
    }

    /**
     * If an IOException gets thrown, it will be converted to a RuntimeException
     */
    @Test
    public void testReadChunkedEncoding_EdgeCase_IOException() {
        InputStream inputStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("test exception only, no worries");
            }
        };

        var exception = assertThrows(RuntimeException.class, () -> inputStreamUtils.readChunkedEncoding(inputStream));

        assertEquals(exception.getMessage(), "java.io.IOException: test exception only, no worries");
    }

    @Test
    public void testReadChunkedEncoding_HappyPath() {
        var inputStream = new ByteArrayInputStream("2\r\nab\r\n0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] bytes = inputStreamUtils.readChunkedEncoding(inputStream);
        assertEquals(new String(bytes), "ab");
    }

    /**
     * For the {@link InputStreamUtils#read(int, InputStream)}, if more bytes are sent than
     * the buffer can hold, it will loop, draining the buffer.  The buffer is hardcoded,
     * see the method in question for "typicalBufferSize"
     */
    @Test
    public void testReadingLarge() {
        Properties properties = new Properties();
        properties.setProperty("MAX_READ_SIZE_BYTES", "100000");
        inputStreamUtils = new InputStreamUtils(new Constants(properties));

        ByteArrayInputStream inputStream = new ByteArrayInputStream("a".repeat(10_000).getBytes(StandardCharsets.UTF_8));

        byte[] result = inputStreamUtils.read(10_000, inputStream);

        assertEqualByteArray(result, "a".repeat(10_000).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testReading_EdgeCase_IOException() {
        InputStream inputStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("test exception only, no worries");
            }
        };

        var exception = assertThrows(RuntimeException.class, () -> inputStreamUtils.read(2, inputStream));

        assertEquals(exception.getMessage(), "java.io.IOException: test exception only, no worries");
    }

    /**
     * The {@link InputStreamUtils#read(int, InputStream)} method is given an expected
     * size of content to read.  If we are unable to read the quantity we were told, we
     * will throw an exception about it.
     * <p>
     *     For example, if we are told to expect 10 bytes, but can only read 5 bytes, we
     *     will throw the exception.
     * </p>
     */
    @Test
    public void testReading_EdgeCase_DifferentCount() {
        InputStream inputStream = new InputStream() {

            private final byte[] sampleData = new byte[] {123};
            int index = 0;

            @Override
            public int read() {
                if (index < 1) {
                    var data = sampleData[index] & 0xFF;
                    index += 1;
                    return data;
                } else {
                    return -1;
                }
            }
        };

        var exception = assertThrows(ForbiddenUseException.class, () -> inputStreamUtils.read(2, inputStream));

        assertEquals(exception.getMessage(), "length of bytes read (1) must be what we expected (2)");
    }
}
