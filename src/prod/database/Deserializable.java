package database;


/**
 * Used for making deserialization generic across
 * types.  See [deserialize]
 */
interface Deserializable<T> {

    /**
     * Takes a string form of a type and
     * converts it to its type.
     */
    T deserialize(String str);

}
