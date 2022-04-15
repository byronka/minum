package database.owndatabase;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static utils.Invariants.mustBeTrue;
import static utils.StringUtils.encode;

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


    /**
     * converts the data in this object to a form easily written to disk.
     * See [dataMappings] to see how we map a name to a value
     */
    public String serialize() {
        final var allKeys = getDataMappings().keySet().stream().map(SerializationKeys::getKeyString).toList();
        allKeys.forEach(x -> {
            mustBeTrue(!x.isBlank(), "Serialization keys must match this regex: %s.  Your key was: (BLANK)".formatted(validKeyRegex.pattern()));
            mustBeTrue(validKeyRegex.matcher(x).matches(), "Serialization keys must match this regex: %s.  Your key was: %s".formatted(validKeyRegex.pattern(), x));
        });
        return "{ " + String.join(" , ", serializeDataMappings(getDataMappings())) + " }";
    }

    /**
     * Given a map of {@link SerializationKeys} to strings, return a
     * serialized form (that is, convert it to a list of strings)
     */
    private List<String> serializeDataMappings(Map<SerializationKeys, String> mapOfKeysToStrings) {
        return mapOfKeysToStrings.entrySet().stream().sorted(Comparator.comparing(x -> x.getKey().getKeyString())).map(x -> x.getKey().getKeyString() + ": " + encode(x.getValue())).toList();
    }

    public abstract T deserialize(String serialized);

    /**
     * The directory where this data will be stored. Also
     * used to generally identify this kind of data.
     */
    public abstract String getDataName();

    public abstract T convertTokensToType(Map<SerializationKeys, String> myMap);
}
