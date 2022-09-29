package atqa.database;

/**
 * This datatype allows us to apply easy and simple (well, easier simpler) disk
 * serialization to a collection of data.  See the constituting interfaces,
 * {@link SimpleSerializable} and {@link SimpleIndexed}
 * @param <T> the type of data
 */
public interface SimpleDataType<T> extends SimpleSerializable<T>, SimpleIndexed {}