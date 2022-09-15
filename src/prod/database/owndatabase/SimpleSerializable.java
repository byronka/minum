package database.owndatabase;

/**
 * An interface for types for which we want to enable
 * a simple serialization on.  This is used as part of
 * our simple database
 * @param <T> the type of data we are serializing.  Note that
 *           because interfaces in Java can only be applied to
 *           classes (and not static methods), to use the deserialize
 *           method it makes sense to create a public static
 *           instance variable of the class to use for running
 *           the deserialization method.
 */
public interface SimpleSerializable<T> {

    /**
     * @return this type serialized to a string
     */
    String serialize();

    /**
     * @param serializedText the serialized string
     * @return this type deserialized from a string
     */
    T deserialize(String serializedText);
}
