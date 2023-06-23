package minum.database;

import java.util.Collection;
import java.util.Comparator;

/**
 * An interface used for those types we intend to store serialized on disk
 */
public interface SimpleIndexed {

    /**
     * We need an index so that each piece of data is distinct, even if it has the same data.
     * @return an index for this data, used to name a file for this particular instance of data
     */
    Long getIndex();

    /**
     * Calculate what the next index should be for the data.
     * <br><br>
     * The data we use in our application uses indexes (that is, just
     * a plain old number [of Long type]) to distinguish one from
     * another.  When we start the system, we need to calculate what
     * the next index will be (for example, on disk we might already
     * have data with indexes of 1, 2, and 3 - and therefore the next
     * index would be 4).
     * <br><br>
     * If there is no data in the collection, just return the number 1.
     */
    static long calculateNextIndex(Collection<? extends SimpleIndexed> data) {
        return data
                .stream()
                .max(Comparator.comparingLong(SimpleIndexed::getIndex))
                .map(SimpleIndexed::getIndex)
                .orElse(0L) + 1L;
    }
}