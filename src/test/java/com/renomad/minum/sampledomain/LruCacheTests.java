package com.renomad.minum.sampledomain;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.LRUCache;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Map;

import static com.renomad.minum.testing.TestFramework.*;

public class LruCacheTests {

    static private Context context;
    static private TestLogger logger;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("LruCacheTests");
        logger = (TestLogger)context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

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
