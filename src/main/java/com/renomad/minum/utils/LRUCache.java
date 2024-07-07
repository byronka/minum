package com.renomad.minum.utils;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple Least-Recently Used Cache
 * See <a href="https://en.wikipedia.org/wiki/Cache_replacement_policies#Least_recently_used_(LRU)">LRU</a>
 */
public final class LRUCache<K,V> extends LinkedHashMap<K, V> {

    @Serial
    private static final long serialVersionUID = -8687744696157499778L;

    // max number of entries allowed in this cache
    private static final int DEFAULT_MAX_ENTRIES = 100;
    private final int maxSize;

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }

    private LRUCache(int maxSize) {
        // uses the same load factor as found in java.util.Hashmap.DEFAULT_LOAD_FACTOR
        super(maxSize + 1, 0.75f, true);
        this.maxSize = maxSize;
    }

    /**
     * Builds a map that functions as a least-recently used cache.
     * Sets the max size to DEFAULT_MAX_ENTRIES. If you want to specify the max size,
     * use the constructor at {@link #getLruCache(int)}
     * <br>
     * Make sure, when using this, to assign it to a fully-defined
     * type, e.g. {@code Map<String, String> foo = getLruCache()}
     * This is necessary since we provide this as a generic method,
     * and the assignment is what enables Java to determine
     * what types to build.
     */
    public static <K,V> Map<K, V> getLruCache() {
        return getLruCache(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates an LRUCache, allowing you to specify the max size.
     * Alternately, see {@link #getLruCache()}.
     * <br>
     * Make sure, when using this, to assign it to a fully-defined
     * type, e.g. {@code Map<String, String> foo = getLruCache(2)}
     * This is necessary since we provide this as a generic method,
     * and the assignment is what enables Java to determine
     * what types to build.
     */
    public static <K,V> Map<K, V> getLruCache(int maxSize) {
        return new LRUCache<>(maxSize);
    }
}
