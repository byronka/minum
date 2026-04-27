package com.renomad.minum.database;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SerializationUtils.deserializeHelper;
import static com.renomad.minum.utils.SerializationUtils.serializeHelper;

/**
 * Exposes a race condition in AbstractDb.addToIndexes():
 * dataSet.add(dbData) at line 260 is OUTSIDE the synchronized block,
 * while removeFromIndexes() IS fully synchronized.
 *
 * When multiple threads write items that share the same index key,
 * they race on the same unsynchronized HashSet, which can cause
 * ConcurrentModificationException or data corruption.
 *
 * This test uses Db (not DbEngine2) because Db.write() has no
 * write lock — it calls writeToMemory() without synchronization.
 * DbEngine2.write() uses a writeLock that serializes all writes.
 */
public class IndexRaceBugTest {

    private Context context;
    private TestLogger logger;
    private IFileUtils fileUtils;
    static Path testDirectory = Path.of("out/index_race_bug_test/foos");

    @Before
    public void init() {
        context = buildTestingContext("IndexRaceBugTest");
        logger = (TestLogger) context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * Multiple threads write items that all map to the same index key
     * ("shared_color" -> "blue"). This forces concurrent HashSet.add()
     * calls on the same Set instance, outside any synchronization.
     *
     * To maximize contention: use many threads with a CyclicBarrier
     * to synchronize their starts, and also have reader threads
     * iterating the indexed data concurrently (which detects CME).
     *
     * Expected: all items are present in the index after concurrent writes.
     * Bug, now fixed, was: ConcurrentModificationException, missing items, or corruption.
     */
    @Test
    public void test_ConcurrentWritesSameIndexKey_ShouldNotCorrupt() throws Exception {
        Path dbPath = testDirectory.resolve("test_ConcurrentWritesSameIndexKey");
        fileUtils.deleteDirectoryRecursivelyIfExists(dbPath);

        final var db = new Db<>(dbPath, context, ColorItem.INSTANCE);
        // All items will have color="blue", so they all map to the same index key.
        db.registerIndex("color", ColorItem::getColor);

        int writerThreads = 8;
        int readerThreads = 4;
        int writesPerThread = 200;
        CyclicBarrier barrier = new CyclicBarrier(writerThreads + readerThreads);
        ExecutorService executor = Executors.newFixedThreadPool(writerThreads + readerThreads);
        List<Future<?>> futures = new ArrayList<>();

        // Writer threads: all write items with color="blue" → same HashSet
        for (int t = 0; t < writerThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                barrier.await();
                for (int i = 0; i < writesPerThread; i++) {
                    db.write(new ColorItem(0, "blue", "t" + threadId + "_" + i));
                }
                return null;
            }));
        }

        // Reader threads: iterate the indexed data while writers are active.
        // Iteration over the unsynchronized HashSet while it's being modified
        // by writers will throw ConcurrentModificationException.
        for (int t = 0; t < readerThreads; t++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                for (int i = 0; i < writesPerThread * 2; i++) {
                    Collection<ColorItem> items = db.getIndexedData("color", "blue");
                    if (items != null) {
                        int count = 0;
                        for (var item : items) {
                            count++;
                        }
                    }
                }
                return null;
            }));
        }

        // Collect results — any ConcurrentModificationException proves the bug
        List<Throwable> errors = new ArrayList<>();
        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        executor.shutdown();

        // Even if no exception was thrown, check data integrity
        Collection<ColorItem> indexed = db.getIndexedData("color", "blue");
        int expectedCount = writerThreads * writesPerThread;

        if (!errors.isEmpty()) {
            db.stop(10,200);
            throw new AssertionError(
                    "Concurrent writes caused " + errors.size() + " error(s). " +
                    "First error: " + errors.getFirst().getClass().getSimpleName() +
                    ": " + errors.getFirst().getMessage(),
                    errors.getFirst());
        }

        // If no exception, verify data integrity — missing items prove the race
        assertEquals(indexed.size(), expectedCount);

        db.stop(10, 200);
    }

    /**
     * A simple DbData implementation with a "color" field that
     * multiple items will share, causing them to map to the same
     * index partition and race on the same HashSet.
     */
    public static class ColorItem extends DbData<ColorItem> {
        private long index;
        private final String color;
        private final String name;
        static final ColorItem INSTANCE = new ColorItem(0, "", "");

        public ColorItem(long index, String color, String name) {
            this.index = index;
            this.color = color;
            this.name = name;
        }

        public String getColor() { return color; }
        public String getName() { return name; }

        @Override public long getIndex() { return index; }
        @Override public void setIndex(long index) { this.index = index; }
        @Override public String serialize() { return serializeHelper(index, color, name); }

        @Override
        public ColorItem deserialize(String serializedText) {
            final var tokens = deserializeHelper(serializedText);
            return new ColorItem(
                    Long.parseLong(tokens.get(0)),
                    tokens.get(1),
                    tokens.get(2));
        }
    }
}
