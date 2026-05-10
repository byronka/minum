package com.renomad.minum.web;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;

public class UrlEncodedDataGetterTests {

    /**
     * If an exception is thrown while reading using the {@link UrlEncodedDataGetter},
     * it will be wrapped and thrown.
     */
    @Test
    public void testRead_NegativeCase_ExceptionThrown() {
        var inputStream = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("JUST A TEST");
            }
        };
        var ex = assertThrows(WebServerException.class, () -> new UrlEncodedDataGetter(inputStream, new CountBytesRead(), 10).read());

        assertEquals(ex.getMessage(), "Error in UrlEncodedDataGetter.read");
        assertEquals(ex.getCause().getMessage(), "JUST A TEST");
    }
}
