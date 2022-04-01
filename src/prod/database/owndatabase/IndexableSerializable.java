package database.owndatabase;

public abstract class IndexableSerializable extends Serializable {
    /**
     * Gets the current index of this object.  A common pattern in the
     * system is to use the [MutableConcurrentSet.nextIndex], which is an [java.util.concurrent.atomic.AtomicInteger].
     * If you are creating a new index for an item, use [java.util.concurrent.atomic.AtomicInteger.getAndIncrement]
     * to get the current value and increment it in one motion, so we can avoid worrying
     * about thread safety when creating new unique id's per element
     */
    protected abstract Integer getIndex();

    protected static String getDirectoryName() {
        throw new UnsupportedOperationException("You must implement this in your entity");
    }
}
