package atqa.utils;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This uses a [ConcurrentHashMap] as its base.  We store
 * the data in the keys only.  We provide some syntactic sugar
 * so this seems similar to using a Set.
 *
 * This is a thread-safe data structure.
 */
public class ConcurrentSet<T> implements Iterable<T> {

    private final ConcurrentHashMap<T, NullEnum> map;

    public ConcurrentSet() {
        this.map = new ConcurrentHashMap<>();
    }

    public boolean add(T element) {
        map.putIfAbsent(element, NullEnum.NULL);
        return true;
    }

    public boolean remove(T element) {
        return map.remove(element) != null;
    }

    public int size() {
        return map.size();
    }

    public boolean contains(T element) {
        return map.containsKey(element);
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableSet(map.keySet(NullEnum.NULL)).iterator();
    }

    private enum NullEnum {
        /**
         * This is just a token for the value in the ConcurrentHashMap, since
         * we are only using the keys, never the values.
         */
        NULL
    }

    public Stream<T> asStream() {
        return asStream(false);
    }

    public Stream<T> asStream(boolean parallel) {
        Iterable<T> iterable = this;
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }
}
