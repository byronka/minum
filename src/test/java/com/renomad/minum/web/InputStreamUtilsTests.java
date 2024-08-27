package com.renomad.minum.web;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.UtilsException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.renomad.minum.testing.TestFramework.*;

public class InputStreamUtilsTests {

    private IInputStreamUtils inputStreamUtils;
    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("input stream utils tests");
        inputStreamUtils = new InputStreamUtils(context.getConstants().maxReadLineSizeBytes);
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * For the {@link InputStreamUtils#read(int, InputStream)}, if more bytes are sent than
     * the buffer can hold, it will loop, draining the buffer.  The buffer is hardcoded,
     * see the method in question for "typicalBufferSize"
     */
    @Test
    public void testReadingLarge() {
        inputStreamUtils = new InputStreamUtils(context.getConstants().maxReadLineSizeBytes);

        ByteArrayInputStream inputStream = new ByteArrayInputStream("a".repeat(10_000).getBytes(StandardCharsets.UTF_8));

        byte[] result = inputStreamUtils.read(10_000, inputStream);

        assertEqualByteArray(result, "a".repeat(10_000).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testReading_EdgeCase_IOException() throws IOException {
        try (InputStream inputStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("test exception only, no worries");
            }
        }) {

            var exception = assertThrows(UtilsException.class, () -> inputStreamUtils.read(2, inputStream));

            assertEquals(exception.getMessage(), "java.io.IOException: test exception only, no worries");
        }
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
    public void testReading_EdgeCase_DifferentCount() throws IOException {
        try (InputStream inputStream = new InputStream() {

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
        }) {

            var exception = assertThrows(ForbiddenUseException.class, () -> inputStreamUtils.read(2, inputStream));

            assertEquals(exception.getMessage(), "length of bytes read (1) must be what we expected (2)");
        }
    }

    @Test
    public void testEquals() {
        EqualsVerifier.forClass(InputStreamUtils.class).verify();
    }
}
