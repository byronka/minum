package com.renomad.minum.sampledomain;

import com.renomad.minum.utils.LRUCache;
import org.junit.Test;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class LruCacheTests {

    public LruCacheTests() {
    }

    /*
     * The LRU Cache (LRUCache) is a useful cache, based on
     * LinkedHashMap, which is in the Java standard library.
     *
     * Simply: When you add something to the cache, and there's
     * no more room, it just drops the oldest element. If you get
     * something, it should make that thing "new" again.
     */
    @Test
    public void test_LRUCache_HappyPath() {
        Map<String, byte[]> lruCache = LRUCache.getLruCache(2);

        lruCache.put("a", new byte[]{1});
        lruCache.put("body", new byte[]{2});
        lruCache.put("c", new byte[]{3});

        assertEquals(lruCache.size(), 2);
        assertEqualByteArray(lruCache.get("body"), new byte[]{2});
        assertEqualByteArray(lruCache.get("c"), new byte[]{3});
        assertTrue(lruCache.get("a") == null);
    }

    /**
     * if we get an item from the LRU cache, it should avoid being least recently used
     */
    @Test
    public void test_GetItem_NotOldest() {
        Map<String, byte[]> lruCache = LRUCache.getLruCache(2);

        lruCache.put("a", new byte[]{1});
        lruCache.put("body", new byte[]{2});
        lruCache.get("a");
        lruCache.put("c", new byte[]{3});

        assertEquals(lruCache.size(), 2);
        assertEqualByteArray(lruCache.get("a"), new byte[]{1});
        assertEqualByteArray(lruCache.get("c"), new byte[]{3});
        assertTrue(lruCache.get("body") == null);
    }
}
