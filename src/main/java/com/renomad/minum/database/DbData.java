package com.renomad.minum.database;

/**
 * An abstract data type meant to be used with {@link Db}
 */
public abstract class DbData<T>{

    /**
     * Serializes this object into a string representation.  It will be
     * the values of this object as strings, encoded with URL encoding,
     * separated by pipe symbols.
     * @return this type serialized to a string - use {@link com.renomad.minum.utils.SerializationUtils#serializeHelper(Object[])}
     */
    protected abstract String serialize();

    /**
     * deserializes the text back into an object.  See helper
     * method {@link com.renomad.minum.utils.SerializationUtils#deserializeHelper(String)} to split a serialized
     * string into tokens for rebuilding the object.  See
     * also {@link #serialize()}
     * @param serializedText the serialized string
     * @return this type deserialized from a string
     */
    protected abstract T deserialize(String serializedText);

    /**
     * We need an index so that each piece of data is distinct, even if it has the same data.
     *
     * @return an index for this data, used to name a file for this particular instance of data
     */
    protected abstract long getIndex();

    /**
     * It is necessary for this method to exist because it is used
     * by the {@link Db} code to add the new index into this data.
     * <p>
     *     Let me unpack that a bit.
     * </p>
     * <p>
     *     The way our database works, it's expected that when you are creating
     *     a new instance of data, you won't know its index yet, because that
     *     is something the database manages for you.
     * </p>
     * <p>
     *     The index is an {@link java.util.concurrent.atomic.AtomicLong} value
     *     that allows us to create new data without worrying about race
     *     conditions between threads (that is, we don't have to worry about
     *     two threads accidentally adding the same index value to two different datas).
     * </p>
     * <p>
     *     This value is also used as the name for the file of this data stored on disk.
     * </p>
     */
    protected abstract void setIndex(long index);

    protected DbData() {}
}
