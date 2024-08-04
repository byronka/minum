package com.renomad.minum.utils;

import java.util.List;

/**
 * Handy helpers when working with bytes
 */
public final class ByteUtils {

    private ByteUtils() {}

    /**
     * A helper method to reduce some of the boilerplate
     * code when converting a list of bytes to an array.
     * <p>
     *     Often, we are gradually building up a list - the list takes
     *     care of accommodating more elements as necessary. An
     *     array, in contrast, is just a single size and doesn't
     *     resize itself.  It's much less convenient to use, so we
     *     more often use lists.
     * </p>
     */
    public static byte[] byteListToArray(List<Byte> result) {
        final var resultArray = new byte[result.size()];
        for(int i = 0; i < result.size(); i++) {
            resultArray[i] = result.get(i);
        }
        return resultArray;
    }

}
