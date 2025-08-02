package com.renomad.minum.web;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.UtilsException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

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
    public void testWeGetNullIndicatingFinish() throws IOException {
        String threeNewlines = "\n\n";
        InputStream inputStream = new ByteArrayInputStream(threeNewlines.getBytes(StandardCharsets.UTF_8));
        assertEquals(inputStreamUtils.readLine(inputStream), "");
        assertEquals(inputStreamUtils.readLine(inputStream), "");
        assertTrue(inputStreamUtils.readLine(inputStream) == null, "The third time we read, we're " +
                "at the end of stream so we get a clear alert that we are done, by receiving a null");
    }

    @Test
    public void testEquals() {
        EqualsVerifier.forClass(InputStreamUtils.class).verify();
    }

    /**
     * Currently taking more than 3 seconds for 200k loops
     */
    @Test
    public void testPerformance() throws IOException {
        String headers = createSampleDataForPerfTest();
        InputStream inputStream = new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8));
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();
        int iterationCount = 20;
        for (int i = 0; i < iterationCount; i++) {
            String foo = "";
            do {
                foo = inputStreamUtils.readLine(inputStream);
            } while (foo != null);
            inputStream.reset();
        }
        long l = stopwatchUtils.stopTimer();
        context.getLogger().logDebug(() -> "Took " + l + " milliseconds to process " + iterationCount + " times in InputStreamUtilsTests.testPerformance");
        assertTrue(l < 100, "Should have taken less than 100 milliseconds");
    }


    /**
     * Similar to {@link #testPerformance()} except that it works
     * through in parallel.  Takes about 400 milliseconds for 200k loops.
     */
    @Test
    public void testParallelPerformance() {
        String headers = createSampleDataForPerfTest();
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();

        int iterationCount = 10;
        IntStream.range(0, iterationCount).boxed().parallel().forEach(x -> {
            InputStream inputStream = new ByteArrayInputStream(headers.getBytes(StandardCharsets.UTF_8));
            String foo = "";
            do {
                try {
                    foo = inputStreamUtils.readLine(inputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } while (foo != null);
        });
        long l = stopwatchUtils.stopTimer();
        context.getLogger().logDebug(() -> "Took " + l + " milliseconds to process " + iterationCount + " times in InputStreamUtilsTests.testPerformance");
        assertTrue(l < 100, "Should have taken less than 100 milliseconds");
    }

    private String createSampleDataForPerfTest() {
        return """
                GET / HTTP/1.1\r
                Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\r
                Accept-Encoding: gzip, deflate, br, zstd\r
                Accept-Language: en-US,en;q=0.9\r
                Connection: keep-alive\r
                Host: minum.com\r
                Referer: https://minum.com/login\r
                Sec-Fetch-Dest: document\r
                Sec-Fetch-Mode: navigate\r
                Sec-Fetch-Site: same-origin\r
                Sec-Fetch-User: ?1\r
                Upgrade-Insecure-Requests: 1\r
                User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36\r
                sec-ch-ua: "Chromium";v="136", "Google Chrome";v="136", "Not.A/Brand";v="99"\r
                sec-ch-ua-mobile: ?0\r
                sec-ch-ua-platform: "Windows"\r
                \r
                """;
    }
}
