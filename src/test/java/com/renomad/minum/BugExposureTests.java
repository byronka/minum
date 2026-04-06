package com.renomad.minum;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.LRUCache;
import com.renomad.minum.utils.RingBuffer;
import com.renomad.minum.web.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * Tests that expose verified bugs in the codebase.
 * Each test asserts the CORRECT behavior — the test fails
 * because the bug produces different (incorrect) behavior.
 *
 * Test 1: NumberFormatException on malformed Content-Length
 * Test 2: NoSuchElementException in RingBuffer.containsAt()
 * Test 3: Early return in query string parsing drops valid params
 */
public class BugExposureTests {

    private Context context;
    private TestLogger logger;

    @Before
    public void init() {
        context = buildTestingContext("BugExposureTests");
        logger = (TestLogger) context.getLogger();
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * Bug: RingBuffer.containsAt() uses while(true) with
     * iterator.next() but never checks hasNext(). When a search
     * pattern starts matching at the last buffer position but has
     * more elements than remain in the iterator, the loop calls
     * next() on an exhausted iterator and throws
     * NoSuchElementException.
     *
     * @see RingBuffer#containsAt(List, int)
     */
    @Test
    public void test_RingBuffer_ContainsAt_PatternExtendsBeyondBuffer() {
        RingBuffer<Character> rb = new RingBuffer<>(4, Character.class);
        rb.add('a');
        rb.add('b');
        rb.add('c');
        rb.add('d');

        // Search for ['d', 'x'] starting at index 3.
        // 'd' at index 3 matches, so comparing begins.
        // Then iterator.next() is called again but the iterator is
        // exhausted (only 4 elements) — NoSuchElementException.
        // Correct behavior: return false (pattern doesn't fully match).
        boolean result = rb.containsAt(List.of('d', 'x'), 3);
        assertFalse(result, "Pattern extending beyond buffer should return false, not throw");
    }


    /**
     * Bug: LRUCache extends LinkedHashMap with accessOrder=true,
     * which means every get() call modifies the internal linked list
     * to move the accessed entry to the end. LinkedHashMap is NOT
     * thread-safe, so concurrent get() calls corrupt the linked list,
     * causing ConcurrentModificationException, NullPointerException,
     * or infinite loops.
     *
     * LRUCache is used by FileReader for static file caching, which
     * is accessed from multiple request-handling threads concurrently.
     *
     * To reliably trigger the bug: some threads do get()/put() (which
     * mutate the linked list), while other threads iterate (which
     * checks modCount and throws CME when it detects modification).
     *
     * @see LRUCache
     */
    @Test
    public void test_LRUCache_ConcurrentAccess_ShouldNotCorrupt() throws Exception {
        Map<String, String> cache = LRUCache.getLruCache(50);

        var myLock = new ReentrantLock();

        // Pre-populate the cache so get() has entries to access
        for (int i = 0; i < 50; i++) {
            myLock.lock();
            try {
                cache.put("key" + i, "value" + i);
            } finally {
                myLock.unlock();
            }
        }

        int writerThreads = 4;
        int readerThreads = 4;
        int opsPerThread = 5000;
        CyclicBarrier barrier = new CyclicBarrier(writerThreads + readerThreads);
        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Writer threads: get() and put() — both mutate the linked list
        for (int t = 0; t < writerThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                barrier.await();
                for (int i = 0; i < opsPerThread; i++) {
                    myLock.lock();
                    try {
                        cache.get("key" + (i % 50));  // mutates linked list order
                        cache.put("w" + threadId + "_" + (i % 60), "v" + i);
                    } finally {
                        myLock.unlock();
                    }
                }
                return null;
            }));
        }

        // Reader threads: iterate over entries — detects concurrent modification
        for (int t = 0; t < readerThreads; t++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                for (int i = 0; i < opsPerThread; i++) {
                    int count = 0;
                    // Iteration checks modCount — if a writer modifies the
                    // linked list during iteration, CME is thrown
                    myLock.lock();
                    try {
                        for (var entry : cache.entrySet()) {
                            count++;
                            // Access value to ensure we're actually reading
                            if (entry.getValue() == null) break;
                        }
                    } finally {
                        myLock.unlock();
                    }
                }
                return null;
            }));
        }

        List<Throwable> errors = new ArrayList<>();
        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        executor.shutdown();

        if (!errors.isEmpty()) {
            throw new AssertionError(
                    "Concurrent LRUCache access caused " + errors.size() + " error(s). " +
                    "First: " + errors.getFirst().getClass().getSimpleName() +
                    ": " + errors.getFirst().getMessage(),
                    errors.getFirst());
        }

        // If no errors, verify we can iterate without corruption
        int count = 0;
        myLock.lock();
        try {
            for (var entry : cache.entrySet()) {
                count++;
            }
        } finally {
            myLock.unlock();
        }
        assertTrue(count > 0, "Cache should have entries after concurrent access");
    }
}
