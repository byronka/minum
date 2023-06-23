package minum.database;

import minum.utils.StringUtils;

import java.util.List;

/**
 * An interface for types for which we want to enable
 * serialization on.  This is used as part of
 * our database
 * @param <T> the type of data we are serializing.  Note that
 *           because interfaces in Java can only be applied to
 *           classes (and not static methods), to use the deserialize
 *           method it makes sense to create a public static
 *           instance variable of the class to use for running
 *           the deserialization method.
 */
public interface SimpleSerializable<T> {

    static String serializeHelper(Object... values) {
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
     * Serializes this object into a string representation.  It will be
     * the values of this object as strings, encoded with URL encoding,
     * separated by pipe symbols.
     * @return this type serialized to a string - use {@link #serializeHelper(Object[])}
     */
    String serialize();

    /**
     * Splits up a string based on a pipe character.  See {@link StringUtils#tokenizer(String, char)}
     *
     * @param serializedText the string we are splitting into tokens
     */
    static List<String> deserializeHelper(String serializedText) {
        return StringUtils.tokenizer(serializedText, '|').stream().map(StringUtils::decode).toList();
    }

    /**
     * deserializes the text back into an object.  See helper
     * method {@link #deserializeHelper(String)} to split a serialized
     * string into tokens for rebuilding the object.  See
     * also {@link #serialize()}
     * @param serializedText the serialized string
     * @return this type deserialized from a string
     */
    T deserialize(String serializedText);
}
