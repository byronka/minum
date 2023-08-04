package minum.database;

import minum.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Extend from this class to create a data value that is
 * intended to be stored in the database.
 * This datatype allows us to apply easy and simple (well, easier simpler) disk
 * serialization to a collection of data.
 * @param <T> the type of data
 */
public abstract class DbData<T>{

    /**
     * Serializes this object into a string representation.  It will be
     * the values of this object as strings, encoded with URL encoding,
     * separated by pipe symbols.
     * @return this type serialized to a string - use {@link minum.utils.SerializationUtils#serializeHelper(Object[])}
     */
    protected abstract String serialize();

    /**
     * deserializes the text back into an object.  See helper
     * method {@link minum.utils.SerializationUtils#deserializeHelper(String)} to split a serialized
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

    protected abstract void setIndex(long index);

    protected DbData() {}
}
