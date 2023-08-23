package minum.utils;

import minum.database.DbData;

import java.util.ArrayList;
import java.util.List;

public final class SerializationUtils {

    private SerializationUtils() {
        // not meant to be constructed.
    }

    /**
     * This is a helper that will encode the values you give it
     * in preparation for storage in a dataase file.
     * <p>
     *     <em>Please note</em>: There is minimal help here.  You
     *     need to keep track of which values will be encoded here.
     *     If you are missing any, this method won't complain.
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
     * Splits up a string based on a pipe character.  See {@link #tokenizer(String, char)}
     *
     * @param serializedText the string we are splitting into tokens
     */
    public static List<String> deserializeHelper(String serializedText) {
        return tokenizer(serializedText, '|').stream().map(StringUtils::decode).toList();
    }

    /**
     * Splits up a string into tokens.
     * @param serializedText the string we are splitting up
     * @param delimiter the character acting as a boundary between sections
     * @return a list of strings.  If the delimiter is not found, we will just return the whole string
     */
    public static List<String> tokenizer(String serializedText, char delimiter) {
        final var resultList = new ArrayList<String>();
        var currentPlace = 0;
        int maxTokens = 200;
        for(int i = 0; i <= maxTokens; i++) {
            final var nextPipeSymbolIndex = serializedText.indexOf(delimiter, currentPlace);
            if (nextPipeSymbolIndex == -1) {
                // if we don't see any pipe symbols ahead, grab the rest of the text from our current place
                resultList.add(serializedText.substring(currentPlace));
                break;
            }
            resultList.add(serializedText.substring(currentPlace, nextPipeSymbolIndex));
            currentPlace = nextPipeSymbolIndex + 1;
        }

        return resultList;
    }
}
