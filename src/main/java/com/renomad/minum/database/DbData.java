package com.renomad.minum.database;

/**
 * An abstract data type meant to be used with the database
 * <p>
 *     The Minum database is very plain - data is stored in a strongly-typed
 *     Java data collection in memory, and also written to disk.
 * </p>
 * <p>
 *     Each database is responsible for one type of data. For example,
 *     you might create a database for Person information, such as name,
 *     age, and favorite ice cream flavor.  To do so, you would design a
 *     class representing the Person, which would include those properties.
 * </p>
 * <p>
 *     Users must supply an implementation of the {@link #serialize()} and
 *     {@link #deserialize(String)} methods.
 * </p>
 */
public abstract class DbData<T>{

    /**
     * Serializes this object into a string representation.
     * <p>
     *     <em>An example:</em>
     * </p>
     * {@snippet :
     *         public String serialize() {
     *             return serializeHelper(index, a, b);
     *         }
     * }
     * @return this type serialized to a string - use {@link com.renomad.minum.utils.SerializationUtils#serializeHelper(Object[])}
     * @see #deserialize(String)
     */
    protected abstract String serialize();

    /**
     * Deserialize the string into a strongly-typed object.  See helper
     * method {@link com.renomad.minum.utils.SerializationUtils#deserializeHelper(String)} to split a serialized
     * string into tokens for rebuilding the object.  See
     * also {@link #serialize()}
     *
     * <p>
     *     <em>An example: </em>
     * </p>
     * {@snippet :
     * public Foo deserialize(String serializedText) {
     *     final var tokens = deserializeHelper(serializedText);
     *     return new Foo(
     *         Integer.parseInt(tokens.get(0)),
     *         Integer.parseInt(tokens.get(1)),
     *         tokens.get(2)
     *         );
     * }
     * }
     * @param serializedText the serialized string
     * @return this type deserialized from a string
     * @see #serialize()
     * @see com.renomad.minum.utils.SerializationUtils#deserializeHelper
     */
    protected abstract T deserialize(String serializedText);

    /**
     * Each piece of data is made unique by having its own index value
     */
    protected abstract long getIndex();

    protected abstract void setIndex(long index);
}
