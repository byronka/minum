package com.renomad.minum.web;

import com.renomad.minum.utils.InvariantException;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertThrows;

public class VaryHeaderTests {

    @Test
    public void testVaryHeader() {
        var varyHeader = new VaryHeader();
        assertEquals(varyHeader.toString(), "Vary: ");

        varyHeader.addHeader("Accept-Encoding");
        assertEquals(varyHeader.toString(), "Vary: Accept-Encoding");

        assertThrows(InvariantException.class,
                "value must not be null",
                () -> varyHeader.addHeader(null));
    }
}
