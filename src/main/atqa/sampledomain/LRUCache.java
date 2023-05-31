package atqa.sampledomain;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple Least-Recently Used Cache for photo serving.
 * See https://en.wikipedia.org/wiki/Cache_replacement_policies#Least_recently_used_(LRU)
 */
public class LRUCache extends LinkedHashMap<String, byte[]> {

    @Serial
    private static final long serialVersionUID = -8687744696157499778L;

    // max number of entries allowed in this cache
    private static final int DEFAULT_MAX_ENTRIES = 100;
    private final int maxSize;

    protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
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
     */
    public static Map<String, byte[]> getLruCache() {
        return getLruCache(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates an LRUCache, allowing you to specify the max size.
     * Alternately, see {@link #getLruCache()}
     */
    public static Map<String, byte[]> getLruCache(int maxSize) {
        return Collections.synchronizedMap(new LRUCache(maxSize));
    }
}
