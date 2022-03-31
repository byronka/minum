package database;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MutableConcurrentSet<T extends IndexableSerializable> implements Iterable<T>, Set<T> {

    private final ConcurrentHashMap<Integer, T> myMap  = new ConcurrentHashMap<>();

    final AtomicInteger nextIndex = new AtomicInteger(1);

    @Override
    public int size() {
        return myMap.size();
    }

    @Override
    public boolean isEmpty() {
        return myMap.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return myMap.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return Set.copyOf(myMap.values()).iterator();
    }

    @Override
    public Object[] toArray() {
        return myMap.entrySet().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
    }

    @Override
    public boolean add(T t) {
        myMap.putIfAbsent(t.getIndex(), t);
        return true;
    }

    /**
     * I have to implement this method, but annoyingly it's of
     * type Object rather than a generic.  I know that when I am
     * using this type, all the objects must be of type
     * {@link IndexableSerializable} and will therefore have
     * getIndex() as a method.  So I have to suppress the warning,
     * since I to know better.
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o) {
        return myMap.remove(((T)o).getIndex()) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
    }

    @Override
    public void clear() {
        myMap.clear();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
//        Iterable.super.forEach(action);
    }

    @Override
    public Spliterator<T> spliterator() {
        throw new UnsupportedOperationException("Have not implemented this, not expected to be used");
//        return Iterable.super.spliterator();
    }
}
