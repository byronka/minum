package database.owndatabase;

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
