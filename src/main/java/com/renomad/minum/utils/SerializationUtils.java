package com.renomad.minum.utils;

import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.security.Inmate;

import java.util.ArrayList;
import java.util.List;

public final class SerializationUtils {

    private SerializationUtils() {
        // not meant to be constructed.
    }

    /**
     * This is a helper that will encode the values you give it
     * in preparation for storage in a database file.
     * <p>
     *     The values will be encoded in URL-encoding (see {@link StringUtils#encode(String)})
     *     and concatenated together with pipe-symbol "|" delimiters.
     * </p>
     * <p>
     *     <em>Please note</em>: You need to keep track of value order,
     *     and making sure all the values are accounted for.
     * </p>
     * <p>
     *     For example, see how this is used in {@link Inmate#serialize()}
     * </p>
     */
    public static String serializeHelper(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length-1; i++) {
            String value = values[i] == null ? null : values[i].toString();
            sb.append(StringUtils.encode(value)).append("|");
        }
        // append the last value with no pipe symbol afterwards
        String lastValue = values[values.length - 1] == null ? null : values[values.length - 1].toString();
        sb.append(StringUtils.encode(lastValue));
        return sb.toString();
    }

    /**
     * Splits up a string based on a pipe character.  See {@link #tokenizer(String, char, int)}
     * <p>
     *     This method is intended to be used as part of the database.  See
     *     the package "com.renomad.minum.database"
     * </p>
     * <p>
     *     For an example, see how this is used in {@link Inmate#deserialize(String)}
     * </p>
     * @param serializedText the string we are splitting into tokens
     */
    public static List<String> deserializeHelper(String serializedText) {
        /*
         * As a general precaution, loops throughout the system have
         * safety limits in place.  In this case, it would be unexpected
         * to have databases of type {@link com.renomad.minum.database.DbData} that
         * have this many fields.
         */
        int maximumDatabasePartitionsAllowed = 200;
        return tokenizer(serializedText, '|', maximumDatabasePartitionsAllowed).stream().map(StringUtils::decode).toList();
    }

    /**
     * Splits up a string into tokens.
     *
     * @param serializedText the string we are splitting up
     * @param delimiter the character acting as a boundary between sections
     * @param maxTokens the maximum tokens allowable.  Probably smart to include a number
     *                  here, since otherwise you could get into some infinite loops.
     * @return a list of strings.  If the delimiter is not found, we will just return the whole string
     */
    public static List<String> tokenizer(String serializedText, char delimiter, int maxTokens) {
        final var resultList = new ArrayList<String>();
        var currentPlace = 0;
        for(int i = 0; ; i++) {
            if (i >= maxTokens) {
                throw new ForbiddenUseException("Asked to split content into too many partitions in the tokenizer.  Current max: " + maxTokens);
            }
            final var nextDelimiterIndex = serializedText.indexOf(delimiter, currentPlace);
            if (nextDelimiterIndex == -1) {
                // if we don't see any delimiters ahead, grab the rest of the text from our current place
                resultList.add(serializedText.substring(currentPlace));
                break;
            }
            resultList.add(serializedText.substring(currentPlace, nextDelimiterIndex));
            currentPlace = nextDelimiterIndex + 1;
        }

        return resultList;
    }

}
