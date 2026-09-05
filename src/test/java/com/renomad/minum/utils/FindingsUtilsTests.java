package com.renomad.minum.utils;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.logging.ThrowingSupplier;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * Deliberately-failing regression tests for defects found in the
 * {@code com.renomad.minum.utils} package.
 * <p>
 *     Each test in this class asserts the <em>correct</em> (desired) behavior,
 *     and therefore fails against the current code.  Each javadoc states the
 *     finding, the location of the defect, and what the code ought to do.
 * </p>
 */
public class FindingsUtilsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("FindingsUtilsTests");
        logger = (TestLogger) context.getLogger();
    }

    @AfterClass
    public static void cleanup() {
        shutdownTestingContext(context);
    }

    @Rule(order = Integer.MIN_VALUE)
    public TestWatcher watchman = new TestWatcher() {
        protected void starting(Description description) {
            logger.test(description.toString());
        }
    };

    /**
     * FINDING 1: a symbolic link inside the static-files directory escapes that directory.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/FileUtils.java:120-121}
     *     (in {@code checkFileIsWithinDirectory}).  Both the directory and the joined
     *     full path are resolved with {@code toRealPath(LinkOption.NOFOLLOW_LINKS)}, which
     *     means no symbolic link is ever resolved.  The containment check therefore only
     *     compares the <em>lexical</em> path, so a symlink that lives inside the static
     *     directory always "starts with" the static directory and passes the check - while
     *     the subsequent read (via {@code RandomAccessFile}, which <em>does</em> follow links)
     *     happily returns the contents of the file the link points at.  This is an arbitrary
     *     file read out of the web root.
     * </p>
     * <p>
     *     Correct behavior: {@code checkFileIsWithinDirectory} must resolve symbolic links
     *     (i.e. use {@code toRealPath()} without {@code NOFOLLOW_LINKS}, or otherwise detect
     *     that the target escapes) and throw {@link ForbiddenUseException} when the real
     *     target of the requested path is outside the given directory.  This must hold for
     *     both a symlink to a file and a symlink to a directory.
     * </p>
     */
    @Test
    public void test_Finding_SymlinkEscapesStaticDirectory() throws IOException {
        Path root = Files.createTempDirectory("minum_finding_symlink_");
        try {
            IFileUtils fileUtils = new FileUtils(logger, context.getConstants());

            Path staticDir = Files.createDirectories(root.resolve("static"));
            Path secretDir = Files.createDirectories(root.resolve("secret"));
            Path secretFile = secretDir.resolve("secret.txt");
            Files.writeString(secretFile, "TOP SECRET - must never be served");

            // a symlink to a file that lives outside the static directory
            Files.createSymbolicLink(staticDir.resolve("link.txt"), secretFile);
            // a symlink to a whole directory that lives outside the static directory
            Files.createSymbolicLink(staticDir.resolve("linkdir"), secretDir);

            String staticDirString = staticDir.toString();

            // demonstrate that the link really does hand out the secret when read
            String leaked = Files.readString(staticDir.resolve("link.txt"), StandardCharsets.UTF_8);
            assertEquals(leaked, "TOP SECRET - must never be served");

            try {
                fileUtils.checkFileIsWithinDirectory("link.txt", staticDirString);
                throw new AssertionError("checkFileIsWithinDirectory should throw ForbiddenUseException for " +
                        "\"link.txt\", because that symbolic link resolves to " + secretFile +
                        ", which is outside the directory " + staticDirString +
                        ".  Instead it allowed the path, which permits reading arbitrary files outside the web root.");
            } catch (ForbiddenUseException ex) {
                // this is the behavior we want
                assertTrue(ex.getMessage() != null, "the ForbiddenUseException should carry an explanatory message");
            }

            try {
                fileUtils.checkFileIsWithinDirectory("linkdir/secret.txt", staticDirString);
                throw new AssertionError("checkFileIsWithinDirectory should throw ForbiddenUseException for " +
                        "\"linkdir/secret.txt\", because the \"linkdir\" symbolic link resolves to " + secretDir +
                        ", which is outside the directory " + staticDirString +
                        ".  Instead it allowed the path, which permits reading an entire arbitrary directory tree.");
            } catch (ForbiddenUseException ex) {
                // this is the behavior we want
                assertTrue(ex.getMessage() != null, "the ForbiddenUseException should carry an explanatory message");
            }
        } finally {
            deleteRecursively(root);
        }
    }

    /**
     * FINDING 2: an absolute STATIC_FILES_DIRECTORY makes every static file read fail.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/FileReader.java:43}.
     *     {@code readFile} calls {@code checkForBadFilePatterns} on the path it is given.
     *     That method is a whitelist intended for the <em>untrusted fragment</em> supplied by
     *     the remote user; among other things it rejects any path whose first character is
     *     "/" or "\\".  But by the time {@code WebFramework.readStaticFile} calls
     *     {@code fileReader.readFile(...)} it has already validated the fragment
     *     ({@code WebFramework.java:632}) and joined it onto {@code STATIC_FILES_DIRECTORY}
     *     ({@code WebFramework.java:611}).  So if an administrator configures an absolute
     *     STATIC_FILES_DIRECTORY (e.g. {@code /var/www/static}), the joined path starts with
     *     "/" and every single static file read throws {@link ForbiddenUseException} - which
     *     {@code WebFramework} treats as an attack and turns into an IP ban of an innocent
     *     visitor.
     * </p>
     * <p>
     *     Correct behavior: {@code FileReader.readFile} must be able to read a legitimate,
     *     existing file addressed by an absolute path.  The untrusted-fragment whitelist
     *     belongs at the point where the untrusted fragment arrives, not on the fully-joined
     *     path.
     * </p>
     */
    @Test
    public void test_Finding_AbsoluteStaticDirectoryPathRejected() throws IOException {
        Path root = Files.createTempDirectory("minum_finding_absolute_");
        try {
            Path staticDir = Files.createDirectories(root.resolve("static"));
            Path indexFile = staticDir.resolve("index.html");
            Files.writeString(indexFile, "<p>hello</p>");

            // this is what WebFramework hands to FileReader when STATIC_FILES_DIRECTORY is absolute
            String joinedAbsolutePath = staticDir.resolve("index.html").toString();

            Map<String, byte[]> lruCache = LRUCache.getLruCache(10);
            var fileReader = new FileReader(lruCache, false, logger);

            byte[] fileContents;
            try {
                fileContents = fileReader.readFile(joinedAbsolutePath);
            } catch (ForbiddenUseException ex) {
                throw new AssertionError("FileReader.readFile should successfully read the legitimate file at the " +
                        "absolute path " + joinedAbsolutePath + " (this is exactly the path WebFramework builds when " +
                        "STATIC_FILES_DIRECTORY is configured as an absolute directory).  Instead it threw " +
                        "ForbiddenUseException: " + ex.getMessage() + " - which WebFramework escalates into an IP ban " +
                        "of an ordinary visitor.", ex);
            }

            assertEquals(new String(fileContents, StandardCharsets.UTF_8), "<p>hello</p>");
        } finally {
            deleteRecursively(root);
        }
    }

    /**
     * FINDING 3: a read/read race in the static file cache makes {@code readFile} return null.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/FileReader.java:33}.
     *     {@code readFile} tests {@code lruCache.containsKey(path)} <em>outside</em>
     *     {@code cacheLock}, and only then takes the lock and calls {@code lruCache.get(path)}.
     *     The cache is an {@link LRUCache} (a {@code LinkedHashMap} in access order), so any
     *     other thread's {@code get} or {@code put} can evict the entry in between.  When that
     *     happens {@code get} returns null and {@code readFile} returns <strong>null</strong>
     *     to its caller (and would NPE outright if TRACE logging were enabled, since the trace
     *     lambda dereferences {@code bytes.length}).  A null return from here becomes a
     *     NullPointerException inside {@code WebFramework.createOkResponseForStaticFiles}, i.e.
     *     a random 500 on a perfectly valid static asset under load.
     * </p>
     * <p>
     *     Correct behavior: {@code readFile} must never return null.  The containment test and
     *     the retrieval must happen atomically under the same lock (a single {@code get} whose
     *     null result falls through to reading the file from disk).
     * </p>
     */
    @Test
    public void test_Finding_LruCacheReadRaceReturnsNull() throws Exception {
        final int numberOfFiles = 40;
        final int cacheSize = 5;
        final int numberOfThreads = 16;
        final int readsPerThread = 2_000;
        // The race is narrow, so the same experiment is repeated a few times to keep this
        // test dependable on slower or less parallel machines.  We stop as soon as we have
        // proof, so the usual cost of this test is a single round.
        final int maximumRounds = 10;

        // Note: these files must be addressed by a *relative* path, because FileReader
        // rejects absolute paths (see test_Finding_AbsoluteStaticDirectoryPathRejected).
        // "target" is build output and is wiped by "mvn clean".
        Path directory = Path.of("target/findings_lru_cache_race");
        deleteRecursively(directory);
        Files.createDirectories(directory);
        try {
            List<String> paths = new ArrayList<>();
            for (int i = 0; i < numberOfFiles; i++) {
                String name = "file_" + i + ".txt";
                Files.writeString(directory.resolve(name), "contents of file number " + i);
                paths.add(directory + "/" + name);
            }

            int totalNulls = 0;
            int totalReads = 0;
            int roundsRun = 0;
            for (int round = 0; round < maximumRounds && totalNulls == 0; round++) {
                roundsRun += 1;
                totalNulls += countNullsFromConcurrentReads(paths, cacheSize, numberOfThreads, readsPerThread);
                totalReads += numberOfThreads * readsPerThread;
            }

            assertTrue(totalNulls == 0,
                    "FileReader.readFile must never return null - it returned null " + totalNulls + " time(s) out of " +
                            totalReads + " concurrent reads (" + roundsRun + " round(s)), because containsKey is checked " +
                            "outside cacheLock and the entry can be evicted before the get inside the lock.  A null here " +
                            "becomes a NullPointerException and a 500 response for a valid static file.");
        } finally {
            deleteRecursively(directory);
        }
    }

    /**
     * Runs one round of the concurrent-read experiment and returns how many times
     * {@link FileReader#readFile(String)} handed back null.
     */
    private static int countNullsFromConcurrentReads(
            List<String> paths, int cacheSize, int numberOfThreads, int readsPerThread) throws Exception {
        Map<String, byte[]> lruCache = LRUCache.getLruCache(cacheSize);
        // a no-op logger is used here on purpose: TestLogger serializes every log call
        // through a single lock, which would mask the concurrency defect under test.
        var fileReader = new FileReader(lruCache, true, new QuietLogger());

        var nullCount = new AtomicInteger(0);
        var unexpectedException = new AtomicReference<Throwable>();
        var startLatch = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < numberOfThreads; t++) {
            final int threadIndex = t;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < readsPerThread; i++) {
                        String path = paths.get((i + threadIndex) % paths.size());
                        byte[] result = fileReader.readFile(path);
                        if (result == null) {
                            nullCount.incrementAndGet();
                        }
                    }
                } catch (Throwable ex) {
                    unexpectedException.compareAndSet(null, ex);
                }
            });
            thread.setName("lru-race-" + t);
            threads.add(thread);
            thread.start();
        }

        startLatch.countDown();
        for (Thread thread : threads) {
            thread.join(TimeUnit.MINUTES.toMillis(2));
        }

        Throwable thrown = unexpectedException.get();
        assertTrue(thrown == null,
                "FileReader.readFile should never throw while several threads read cached static files. Instead: " + thrown);
        return nullCount.get();
    }

    /**
     * FINDING 4: {@code RingBuffer.contains} misses a match that begins inside a partial match.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/RingBuffer.java:75-95}.
     *     When the element under the cursor does not continue the partial match, the code
     *     resets {@code myListIndex} to 0 but does <em>not</em> re-test the current element
     *     against the first element of the sought list.  Any repeated prefix in the search
     *     term therefore causes a false negative.  {@code RingBuffer.contains} is used by the
     *     multipart body processor and the HTML parser, so a boundary or delimiter with a
     *     repeated prefix is silently not found.
     * </p>
     * <p>
     *     Correct behavior: {@code contains} must report true whenever the sought sequence
     *     appears anywhere in the buffer.  "aaab" contains "aab", "xaaay" contains "aay",
     *     and "abcabcabd" contains "abcabd".
     * </p>
     */
    @Test
    public void test_Finding_RingBufferContainsRepeatedPrefix() {
        assertTrue(buildRingBuffer("aaab").contains(toCharacterList("aab")),
                "RingBuffer of 'aaab' should report that it contains 'aab' (it appears at index 1)");
        assertTrue(buildRingBuffer("xaaay").contains(toCharacterList("aay")),
                "RingBuffer of 'xaaay' should report that it contains 'aay' (it appears at index 2)");
        assertTrue(buildRingBuffer("abcabcabd").contains(toCharacterList("abcabd")),
                "RingBuffer of 'abcabcabd' should report that it contains 'abcabd' (it appears at index 3)");
    }

    /**
     * FINDING 5: {@code StringUtils.safeAttr} does not escape characters that break out of an attribute.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/StringUtils.java:40-71}.
     *     {@code safeAttr} escapes only {@code &}, {@code <}, {@code "} and {@code '}.  It
     *     leaves {@code >}, {@code =}, the space character and the backtick untouched.  An
     *     unquoted attribute value - which is legal HTML and which the framework's own
     *     templating makes easy to produce, e.g. {@code <div class={{userValue}}>} - is
     *     terminated by a space, so {@code safeAttr("x onmouseover=alert(1)")} is returned
     *     completely unchanged and injects a brand new event-handler attribute.  Internet
     *     Explorer additionally treats a backtick as an attribute delimiter.
     * </p>
     * <p>
     *     Correct behavior: {@code safeAttr} must escape every character that can terminate
     *     an attribute value or introduce a new attribute - at minimum {@code >}, {@code =},
     *     space and backtick, in addition to the four it already handles.
     * </p>
     */
    @Test
    public void test_Finding_SafeAttrEscapesAttributeBreakingCharacters() {
        String attack = "x onmouseover=alert(1)";
        String result = StringUtils.safeAttr(attack);

        assertFalse(result.contains(" "),
                "safeAttr should escape the space character, because a space terminates an unquoted attribute value. " +
                        "safeAttr(\"" + attack + "\") returned \"" + result + "\"");
        assertFalse(result.contains("="),
                "safeAttr should escape the equals sign, because it lets injected text start a new attribute. " +
                        "safeAttr(\"" + attack + "\") returned \"" + result + "\"");
        assertFalse(StringUtils.safeAttr("a>b").contains(">"),
                "safeAttr should escape the greater-than sign, because it can close the enclosing tag. " +
                        "safeAttr(\"a>b\") returned \"" + StringUtils.safeAttr("a>b") + "\"");
        assertFalse(StringUtils.safeAttr("a`b").contains("`"),
                "safeAttr should escape the backtick, which some browsers accept as an attribute delimiter. " +
                        "safeAttr(\"a`b\") returned \"" + StringUtils.safeAttr("a`b") + "\"");
    }

    /**
     * FINDING 6: {@code StringUtils.generateSecureRandomString} silently returns "" for a non-positive length.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/StringUtils.java:96-104}.
     *     The method is {@code IntStream.range(1, length+1)}, so any length of zero or less
     *     produces an empty stream and the method quietly returns the empty string.  This
     *     method is the framework's source of session identifiers and password salts; a
     *     mis-set configuration value or an arithmetic slip would hand back an empty salt or
     *     an empty session token with no warning whatsoever.
     * </p>
     * <p>
     *     Correct behavior: a request for a secure random string of length zero or less is a
     *     programming error and must throw, not return "".
     * </p>
     */
    @Test
    public void test_Finding_GenerateSecureRandomStringRejectsNonPositiveLength() {
        String zeroResult = null;
        RuntimeException zeroException = null;
        try {
            zeroResult = StringUtils.generateSecureRandomString(0);
        } catch (RuntimeException ex) {
            zeroException = ex;
        }
        assertTrue(zeroException != null,
                "generateSecureRandomString(0) should throw, because an empty session token or password salt is " +
                        "never a valid answer.  Instead it returned \"" + zeroResult + "\"");

        String negativeResult = null;
        RuntimeException negativeException = null;
        try {
            negativeResult = StringUtils.generateSecureRandomString(-5);
        } catch (RuntimeException ex) {
            negativeException = ex;
        }
        assertTrue(negativeException != null,
                "generateSecureRandomString(-5) should throw, because a negative length is a programming error.  " +
                        "Instead it returned \"" + negativeResult + "\"");
    }

    /**
     * FINDING 7: {@code SerializationUtils.serializeHelper()} throws a raw ArrayIndexOutOfBoundsException.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/utils/SerializationUtils.java:41}.
     *     After the loop the method unconditionally reads {@code values[values.length - 1]}.
     *     Called with no arguments (easy to do with a varargs method, e.g. when a record has
     *     had all of its fields removed, or when an empty array is spread into it) that index
     *     is -1 and the caller gets a bare {@link ArrayIndexOutOfBoundsException} from deep
     *     inside the database serialization layer.
     * </p>
     * <p>
     *     Correct behavior: the method must validate its input and throw the framework's own
     *     {@link UtilsException} with an explanatory message, consistent with how the rest of
     *     the utils package reports misuse.
     * </p>
     */
    @Test
    public void test_Finding_SerializeHelperRejectsZeroArguments() {
        var ex = assertThrows(UtilsException.class, () -> SerializationUtils.serializeHelper());
        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank(),
                "the UtilsException thrown by serializeHelper() should explain that at least one value is required");
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static List<Character> toCharacterList(String text) {
        List<Character> characters = new ArrayList<>();
        for (char c : text.toCharArray()) {
            characters.add(c);
        }
        return characters;
    }

    private static RingBuffer<Character> buildRingBuffer(String text) {
        var ringBuffer = new RingBuffer<>(text.length(), Character.class);
        for (char c : text.toCharArray()) {
            ringBuffer.add(c);
        }
        return ringBuffer;
    }

    /**
     * Deletes a directory tree.  Note that {@link Files#walk} does not follow
     * symbolic links, so the links themselves get deleted rather than their targets.
     */
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> allPaths;
        try (Stream<Path> walk = Files.walk(path)) {
            allPaths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : allPaths) {
            Files.deleteIfExists(p);
        }
    }

    /**
     * A logger that does nothing at all.  Used by the concurrency test so that
     * the logging subsystem's own locking does not hide the defect under test.
     */
    private static final class QuietLogger implements ILogger {

        private final Map<LoggingLevel, Boolean> activeLogLevels = new EnumMap<>(LoggingLevel.class);

        @Override
        public void logDebug(ThrowingSupplier<String, Exception> msg) {
            // intentionally does nothing
        }

        @Override
        public void logWarn(ThrowingSupplier<String, Exception> msg) {
            // intentionally does nothing
        }

        @Override
        public void logTrace(ThrowingSupplier<String, Exception> msg) {
            // intentionally does nothing
        }

        @Override
        public void logAsyncError(ThrowingSupplier<String, Exception> msg) {
            // intentionally does nothing
        }

        @Override
        public void logAudit(ThrowingSupplier<String, Exception> msg) {
            // intentionally does nothing
        }

        @Override
        public void stop() {
            // intentionally does nothing
        }

        @Override
        public Map<LoggingLevel, Boolean> getActiveLogLevels() {
            return activeLogLevels;
        }
    }
}
