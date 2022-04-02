package database.owndatabase;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static utils.Invariants.mustBeTrue;

/**
 * Serializable classes are able to [serialize] their content
 */
abstract class Serializable<T> {

    /**
     * this represents the connection between a name of a property
     * and the associated value of it in this class,
     * used during the serialization process.  For example,
     * if we think about a type with an id
     * and a name, an appropriate map might
     * be "name" to encode(name) and "id" to id.
     *
     * Note that encoding values before serialization is key, so that
     * whitespace and symbols don't cause us stomach upset when decoding.
     */
    public abstract Map<SerializationKeys, String> getDataMappings();

    /**
     * This is used as a boundary of what is acceptable for a key string
     * used in our serialization process.  There's no need to complicate
     * things.  Keys should be short and sweet.  No need for symbols or
     * numbers or whitespace - it just would complicate deserialization later.
     */
    public static final Pattern validKeyRegex = Pattern.compile("[a-zA-Z]{1,10}");

    public abstract String serialize();
    public abstract T deserialize(String serialized);

    /**
     * The directory where this data will be stored
     */
    public abstract String getDirectoryName();

    /**
     * Converts a string to a [SerializationKeys]
     */
    public <T extends SerializationKeys> SerializationKeys convertToKey(String s, List<T> values) {
        final var foo = values.stream().filter(x -> x.getKeyString().equals(s)).toList();
        mustBeTrue(foo.size() == 1, "There must be exactly one key found");
        return foo.get(0);
    }

}
