package com.renomad.minum.utils;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class RingBuffer<T> implements Iterable<T>{

    /**
     * The size of the inner array supporting this class.  Also,
     * the most items our RingBuffer can contain.
     */
    private final int limit;

    /**
     * The last index of the array - just the limit minus 1.
     */
    private final int lastIndex;

    /**
     * An instance of the class for this generic collection, used to
     * help build data structures.
     */
    private final T[] innerArray;

    /**
     * Index of where we place the next item
     */
    private int nextIndex;

    /**
     * Build a {@link RingBuffer}
     * @param limit the maximum size of the buffer
     */
    public RingBuffer(int limit, Class<T> clazz) {
        this.limit = limit;
        this.lastIndex = limit - 1;
        @SuppressWarnings("unchecked")
        final T[] t = (T[]) Array.newInstance(clazz, limit);
        this.innerArray = t;
        this.nextIndex = 0;
    }

    /**
     * This will move the index forward and
     * wrap around at the end.
     */
    private int incrementIndex(int index) {
        if (index == lastIndex) {
            return 0;
        } else {
            return index + 1;
        }
    }

    public void add(T item) {
        innerArray[nextIndex] = item;
        nextIndex = incrementIndex(nextIndex);
    }

    public int getLimit() {
        return limit;
    }

    /**
     * Returns true if the data in the "myList" parameter is found
     * within the RingBuffer.
     */
    public boolean contains(List<T> myList) {
        if (myList == null || myList.isEmpty()) {
            throw new UtilsException("expected a valid non-empty list to search for in the RingBuffer");
        }
        int myListIndex = 0;
        int myListLength = myList.size();
        for (var value : this) {
            if (myList.get(myListIndex).equals(value)) {
                myListIndex += 1;
            } else {
                myListIndex = 0;
            }

            if (myListIndex == myListLength) {
                return true;
            }

        }
        return false;
    }

    /**
     * Returns true if the data in the "myList" parameter is found
     * within the RingBuffer at the index provided.
     */
    public boolean containsAt(List<T> myList, int index) {
        if (myList == null || myList.isEmpty()) {
            throw new UtilsException("expected a valid non-empty list to search for in the RingBuffer");
        }
        if (index > lastIndex || index < 0) {
            throw new UtilsException("expected an index greater than zero and less-than-or-equal to the last index of the buffer (the limit minus one)");
        }
        int i = 0;
        int myListIndex = 0;
        int myListLastIndex = myList.size() - 1;
        boolean comparing = false;
        var iterator = this.iterator();
        while (true) {
            var value = iterator.next();
            if (i == index) {
                comparing = true;
            }

            if (comparing) {
                if (! myList.get(myListIndex).equals(value)) {
                    return false;
                }
                if (myListIndex == myListLastIndex) {
                    return true;
                }
                myListIndex += 1;
            }

            i += 1;
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {

            /**
             * As we loop through the buffer, we have to use a similar
             * technique to the "nextIndex" - wrapping around when we
             * hit the end
             */
            int iteratorIndex = nextIndex;
            /**
             * We'll count as we read values, stopping when we've read every slot.
             */
            int count = 0;

            @Override
            public boolean hasNext() {
                return count < limit;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                var result = innerArray[iteratorIndex];
                iteratorIndex = incrementIndex(iteratorIndex);
                count += 1;
                return result;
            }
        };
    }

    /**
     * Returns the value at the slot pointed to by the "nextIndex".  Note that
     * this will be null at first while the array starts to get filled.
     */
    public T atNextIndex() {
        return innerArray[nextIndex];
    }

}
