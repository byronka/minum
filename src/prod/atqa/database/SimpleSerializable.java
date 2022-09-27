package atqa.database;

/**
 * An interface for types for which we want to enable
 * a simple serialization on.  This is used as part of
 * our simple atqa.database
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
     *
     * for example,
     *
     *     public String serialize() {
     *         return index + " " + URLEncoder.encode(fullname(), StandardCharsets.UTF_8);
     *     }
     */
    String serialize();

    /**
     * @param serializedText the serialized string
     * @return this type deserialized from a string
     *
     * for example,
     *
     *     public PersonName deserialize(String serializedText) {
     *         final var indexEndOfIndex = serializedText.indexOf(' ');
     *         final var indexStartOfName = indexEndOfIndex + 1;
     *
     *         final var rawStringIndex = serializedText.substring(0, indexEndOfIndex);
     *         final var rawStringName = serializedText.substring(indexStartOfName);
     *
     *         return new PersonName(URLDecoder.decode(rawStringName, StandardCharsets.UTF_8), Integer.parseInt(rawStringIndex));
     *     }
     */
    T deserialize(String serializedText);
}
