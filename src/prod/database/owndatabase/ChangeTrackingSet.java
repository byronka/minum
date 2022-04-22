package database.owndatabase;

import utils.Pair;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static database.owndatabase.ChangeTrackingSet.DataAction.*;

public class ChangeTrackingSet<T extends IndexableSerializable<?>> extends MutableConcurrentSet<T> {

    /**
     * This is used to tag what gets changed, so we
     * know what to do during serialization later.
     * For example, if something was deleted, we
     * would delete the file.
     */
    enum DataAction {
        /*
         * New data is being added
         */
        CREATE,

        /*
         * Data is being deleted from the set
         */
        DELETE,

        /*
         * Update the data in place
         */
        UPDATE,
    }

    public final CustomConcurrentQueue<Pair<T, DataAction>> modified = new CustomConcurrentQueue<>();

    static class CustomConcurrentQueue<T> extends ConcurrentLinkedQueue<T> {

        /**
         * This method is the whole reason behind creating
         * our own queue, it is so we can override the equals
         * to only care about the list, in order, so we
         * can more easily compare equality on the [ConcurrentLinkedQueue]
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other != null && this.getClass() != other.getClass()) return false;

            final var o = (CustomConcurrentQueue<?>) other;

            return this.stream().toList() == o.stream().toList();
        }

    }


    /**
     * clears the set of tracked changed data
     */
    public void clearModifications() {
        modified.clear();
    }


    @Override
    public boolean add(T element) {
        modified.add(new Pair<>(element, CREATE));
        return super.add(element);
    }

    /**
     * Unlike {@link #add(IndexableSerializable)}, this will not put anything into the
     * list of modified data.  This is necessary in
     * some situations, like when deserializing data from disk
     * during system startup.
     */
    public boolean addWithoutTracking(T item) {
        return super.add(item);
    }

    /**
     * Try to remove an element.  If the element is not found in the
     * data (by its id), do nothing and return false.
     */
    public boolean remove(T element) {
        if (this.stream().anyMatch(x -> x.getIndex().equals(element.getIndex()))) {
            modified.add(new Pair<>(element, DELETE));
            return super.remove(element);
        }
        return false;
    }

    /**
     * Updates a value
     *
     * We will find the old element by its id, since this must
     * be of type [IndexableSerializable], it means we have
     * access to the getIndex() command.  Then we will
     * overwrite the value stored.
     *
     */
    @Override
    public boolean update(T element) {
        modified.add(new Pair<>(element, UPDATE));
        return super.update(element);
    }


    public static <K extends IndexableSerializable<?>> ChangeTrackingSet<K> toChangeTrackingSet(List<K> myList) {
        final var newSet = new ChangeTrackingSet<K>();
        newSet.addAll(myList);
        return newSet;
    }



}
