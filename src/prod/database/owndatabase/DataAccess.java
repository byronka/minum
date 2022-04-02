package database.owndatabase;


import utils.Pair;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents the common methods available on data.
 *
 * For read-only situations (no changes being made to the data),
 * you can use [read]
 *
 * For any situation where the data changes, see [actOn]
 *
 * T is the domain-oriented type, such as Project or Employee.
 *        The database expects all data to be a set of [ChangeTrackingSet]
 */
public class DataAccess<T extends IndexableSerializable<?>> {

    private final ChangeTrackingSet<T> data;
    private final DatabaseDiskPersistence dbp;
    private final String name;

    public DataAccess(ChangeTrackingSet<T> data, DatabaseDiskPersistence dbp, String name) {
        this.data = data;
        this.dbp = dbp;
        this.name = name;
    }


    /**
     * carry out some write action on the data.
     *
     * This has to be synchronized because there's no other atomic way to
     * make changes to both the database *and* the disk.
     *
     * @param action a lambda to receive the set of data and do whatever you want with it
     */
    public synchronized <R> R actOn(Function<ChangeTrackingSet<T>, R> action) {
        final var result = action.apply(data);

        if (dbp != null) {
            Pair<T, ChangeTrackingSet.DataAction> nextItem;
            do {
                nextItem = data.modified.poll();
                if (nextItem != null) {
                    switch (nextItem.second) {
                        case CREATE -> dbp.persistToDisk(nextItem.first, name);
                        case DELETE -> handleDeletion(nextItem.first, name, dbp, data);
                        case UPDATE -> dbp.updateOnDisk(nextItem.first, name);
                    }
                }
            } while (nextItem != null);
        }
        return result;
    }

    /**
     * Deletion is a special case.  If items have been deleted so that the next
     * index changes, we account for that here.
     *
     * For example, let's say we have three items, and their indexes are: 1, 2, 3
     *
     * What if we wipe out item 3?  Then we need to adjust our nextIndex counter to
     * have 3 as the next index to assign.
     *
     * What if there is only one item in the set of data, with an index of 18? If
     * we delete that, there are no items in the set and so our next index should be 1.
     *
     * Note that deleting items not at the end shouldn't have much effect.  For example,
     * if we have items 1, 2, 3, and we delete the item with index 2, then nothing changes,
     * we still have a nextIndex of 4.
     */
    private void handleDeletion(T item, String name, DatabaseDiskPersistence dbp, ChangeTrackingSet<T> data)  {
        dbp.deleteOnDisk(item, name);
        final var nextIndexValue = data.stream().map(x -> x.getIndex() ).max(Integer::compare).orElse(0);
        data.nextIndex.set(nextIndexValue + 1);
    }

    /**
     * carry out some readonly action on the data.
     * @param action a lambda to receive the set of data and do whatever you want with it
     */
    public <R> R read(Function<Set<T>, R> action) {
        return action.apply(Collections.unmodifiableSet(data));
    }

}
