package com.renomad.minum.utils;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This uses a [ConcurrentHashMap] as its base.  We store
 * the data in the keys only.  We provide some syntactic sugar
 * so this seems similar to using a Set.
 * <p>
 * This is a thread-safe data structure.
 */
public final class ConcurrentSet<T> implements Iterable<T> {

    private final ConcurrentHashMap<T, NullEnum> map;

    public ConcurrentSet() {
        this.map = new ConcurrentHashMap<>();
    }

    public void add(T element) {
        map.putIfAbsent(element, NullEnum.NULL);
    }

    public void remove(T element) {
        map.remove(element);
    }

    public int size() {
        return map.size();
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
}
