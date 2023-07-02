package minum.database;

import minum.Context;
import minum.utils.StringUtils;

import java.util.List;

public abstract class SimpleDataTypeImpl<T> implements ISimpleDataType<T> {

    protected final Context context;
    private final StringUtils stringUtils;

    public SimpleDataTypeImpl(Context context) {
        this.context = context;
        this.stringUtils = new StringUtils(context);
    }

    protected String serializeHelper(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length-1; i++) {
            String value = values[i] == null ? null : values[i].toString();
            sb.append(stringUtils.encode(value)).append("|");
        }
        // append the last value with no pipe symbol afterwards
        String lastValue = values[values.length - 1] == null ? null : values[values.length - 1].toString();
        sb.append(stringUtils.encode(lastValue));
        return sb.toString();
    }


    /**
     * Splits up a string based on a pipe character.  See {@link StringUtils#tokenizer(String, char)}
     *
     * @param serializedText the string we are splitting into tokens
     */
    protected List<String> deserializeHelper(String serializedText) {
        return stringUtils.tokenizer(serializedText, '|').stream().map(stringUtils::decode).toList();
    }
}
