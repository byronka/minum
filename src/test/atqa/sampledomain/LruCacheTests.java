package atqa.sampledomain;

import atqa.testing.TestLogger;
import atqa.utils.LRUCache;

import java.util.concurrent.ExecutorService;

import static atqa.testing.TestFramework.*;

public class LruCacheTests {

    private final TestLogger logger;

    public LruCacheTests(TestLogger logger) {
        this.logger = logger;
    }

    public void tests(ExecutorService es) {

        /*
         * The LRU Cache (LRUCache) is a useful cache, based on
         * LinkedHashMap, which is in the Java standard library.
         *
         * Simply: When you add something to the cache, and there's
         * no more room, it just drops the oldest element. If you get
         * something, it should make that thing "new" again.
         */
        logger.test("ensure that the LRUCache works as expected - oldest removed"); {
            var lruCache = LRUCache.getLruCache(2);

            lruCache.put("a", new byte[]{1});
            lruCache.put("body", new byte[]{2});
            lruCache.put("c", new byte[]{3});

            assertEquals(lruCache.size(), 2);
            assertEqualByteArray(lruCache.get("body"), new byte[]{2});
            assertEqualByteArray(lruCache.get("c"), new byte[]{3});
            assertTrue(lruCache.get("a") == null);
        }

        logger.test("if we get an item from the LRU cache, it should avoid being least recently used"); {
            var lruCache = LRUCache.getLruCache(2);

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
}
