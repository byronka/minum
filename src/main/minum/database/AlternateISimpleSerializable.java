package minum.database;

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
public interface AlternateISimpleSerializable<T> {


    /**
     * Serializes this object into a string representation.  It will be
     * the values of this object as strings, encoded with URL encoding,
     * separated by pipe symbols.
     * @return this type serialized to a string - use {@link SimpleDataTypeImpl#serializeHelper(Object[])}
     */
    String serialize();

    /**
     * deserializes the text back into an object.  See helper
     * method {@link SimpleDataTypeImpl#deserializeHelper(String)} to split a serialized
     * string into tokens for rebuilding the object.  See
     * also {@link #serialize()}
     * @param serializedText the serialized string
     * @return this type deserialized from a string
     */
    T deserialize(String serializedText);
}
