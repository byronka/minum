package minum.database;

import minum.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Extend from this class to create a data value that is
 * intended to be stored in the database.
 */
public abstract class SimpleDataTypeImpl<T> implements ISimpleDataType<T> {


    protected SimpleDataTypeImpl() {
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
    protected String serializeHelper(Object... values) {
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
     * Splits up a string based on a pipe character.  See {@link StringUtils#tokenizer(String, char)}
     *
     * @param serializedText the string we are splitting into tokens
     */
    protected List<String> deserializeHelper(String serializedText) {
        return tokenizer(serializedText, '|').stream().map(StringUtils::decode).toList();
    }

    /**
     * Splits up a string into tokens.
     * @param serializedText the string we are splitting up
     * @param delimiter the character acting as a boundary between sections
     * @return a list of strings.  If the delimiter is not found, we will just return the whole string
     */
    private List<String> tokenizer(String serializedText, char delimiter) {
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
