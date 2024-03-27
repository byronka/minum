package com.renomad.minum.utils;

import org.junit.Test;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.assertEqualByteArray;

public class ByteUtilsTests {

    @Test
    public void testConversionToArray() {
        assertEqualByteArray(new byte[]{1,2,3}, ByteUtils.byteListToArray(List.of((byte)1, (byte)2, (byte)3)));
        assertEqualByteArray(new byte[0], ByteUtils.byteListToArray(List.of()));
    }
}
