package com.renomad.minum.security;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.IFileUtils;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.web.FullSystem;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static com.renomad.minum.testing.TestFramework.*;

public class TheBrigTests {

    private static Context context;
    private static TestLogger logger;
    private static IFileUtils fileUtils;

    @BeforeClass
    public static void init() {
        var props = new Properties();
        props.setProperty("DB_DIRECTORY", "out/brigdb");
        props.setProperty("IS_THE_BRIG_ENABLED", "true");
        context = buildTestingContext("unit_tests", props);
        logger = (TestLogger) context.getLogger();
        fileUtils = new FileUtils(logger, context.getConstants());
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
     * A user should be able to put a particular address in jail for
     * a time and after it has paid its dues, be released.
     */
    @Test
    public void test_TheBrig_Basic() throws IOException {
        MyThread.sleep(50);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        var b = new TheBrig(10, context).initialize();
        // give the database time to start
        MyThread.sleep(20);

        // send some clients to jail
        ITheBrig finalB = b;
        finalB.sendToJail("1.1.1.1_too_freq_downloads", 10000);
        finalB.sendToJail("2.2.2.2_too_freq_downloads", 20);
        finalB.sendToJail("3.3.3.3_too_freq_downloads", 20);
        finalB.sendToJail("4.4.4.4_too_freq_downloads", 20);

        // what's the situation?
        assertTrue(finalB.isInJail("1.1.1.1_too_freq_downloads"), "1.1.1.1_too_freq_downloads should be in jail");
        assertTrue(finalB.isInJail("2.2.2.2_too_freq_downloads"), "2.2.2.2_too_freq_downloads should be in jail");
        assertTrue(finalB.isInJail("3.3.3.3_too_freq_downloads"), "3.3.3.3_too_freq_downloads should be in jail");
        assertTrue(finalB.isInJail("4.4.4.4_too_freq_downloads"), "4.4.4.4_too_freq_downloads should be in jail");
        assertFalse(finalB.isInJail("DOES_NOT_EXIST"));

        MyThread.sleep(10);

        // after a short time, they should all still be in jail
        assertTrue(finalB.isInJail("1.1.1.1_too_freq_downloads"), "1.1.1.1_too_freq_downloads should be in jail");
        assertTrue(finalB.isInJail("2.2.2.2_too_freq_downloads"), "2.2.2.2_too_freq_downloads should be in jail");
        assertTrue(finalB.isInJail("3.3.3.3_too_freq_downloads"), "3.3.3.3_too_freq_downloads should be in jail");
        assertTrue(finalB.isInJail("4.4.4.4_too_freq_downloads"), "4.4.4.4_too_freq_downloads should be in jail");

        MyThread.sleep(80);

        b.stop();

        MyThread.sleep(80);

        b = new TheBrig(10, context).initialize();
        MyThread.sleep(30);

        // after their release time, they should all be out, except 1.1.1.1, who bugged us
        assertTrue( b.isInJail("1.1.1.1_too_freq_downloads"), "1.1.1.1_too_freq_downloads should be in jail");
        assertFalse(b.isInJail("2.2.2.2_too_freq_downloads"), "2.2.2.2_too_freq_downloads should not be in jail");
        assertFalse(b.isInJail("3.3.3.3_too_freq_downloads"), "3.3.3.3_too_freq_downloads should not be in jail");
        assertFalse(b.isInJail("4.4.4.4_too_freq_downloads"), "4.4.4.4_too_freq_downloads should not be in jail");

        // 1.1.1.1 bugged us some more.
        b.sendToJail("1.1.1.1_too_freq_downloads", 40);

        b.stop();

        MyThread.sleep(80);

        b = new TheBrig(10, context).initialize();

        MyThread.sleep(30);

        assertTrue(b.isInJail("1.1.1.1_too_freq_downloads"));
        assertFalse(b.isInJail("2.2.2.2_too_freq_downloads"));
        assertFalse(b.isInJail("3.3.3.3_too_freq_downloads"));
        assertFalse(b.isInJail("4.4.4.4_too_freq_downloads"));

        MyThread.sleep(20);

        assertTrue(b.isInJail("1.1.1.1_too_freq_downloads"));
        assertFalse(b.isInJail("2.2.2.2_too_freq_downloads"));
        assertFalse(b.isInJail("3.3.3.3_too_freq_downloads"));
        assertFalse(b.isInJail("4.4.4.4_too_freq_downloads"));

        b.stop();

        MyThread.sleep(80);
    }

    @Test
    public void test_TheBrig_RegularStop() throws IOException {
        MyThread.sleep(50);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        var b = new TheBrig(10, context).initialize();
        MyThread.sleep(10);
        b.stop();
        assertTrue(true, "We should get here without an exception");
        assertTrue(logger.doesMessageExist("TheBrig has been told to stop", 8));
        assertTrue(logger.doesMessageExist("TheBrig: Sending interrupt to thread", 8));
        assertTrue(logger.doesMessageExist("TheBrig is stopped.", 8));
    }

    /**
     * If the brig isn't initialized, there's no thread to stop
     */
    @Test
    public void test_TheBrig_Uninitialized() throws IOException {
        var b = new TheBrig(10, context);
        var ex = assertThrows(MinumSecurityException.class, b::stop);
        assertEquals(ex.getMessage(), "TheBrig was told to stop, but it was uninitialized");
        context.clearDatabasePaths();
    }

    /**
     * If an inmate was in the brig already and they transgressed again,
     * simply put them back in with a duration starting from the moment
     * of the new transgression.
     */
    @Test
    public void test_TheBrig_ExistingInmate() throws IOException {
        MyThread.sleep(50);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        var b = new TheBrig(10, context).initialize();
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        Long releaseTime = b.getInmates().stream().toList().getFirst().getReleaseTime();
        assertTrue(b.isInJail("1.2.3.4_too_freq_downloads"));
        assertEquals(b.getInmates().size(), 1);
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        assertEquals(releaseTime + 20, b.getInmates().stream().toList().getFirst().getReleaseTime());
        MyThread.sleep(70);
        assertFalse(b.isInJail("1.2.3.4_too_freq_downloads"));
        assertEquals(b.getInmates().size(), 0);
        b.stop();
    }

    @Test
    public void test_Deserialization() {
        Inmate inmate = Inmate.EMPTY.deserialize("123|20.211.213.157_vuln_seeking|1691728931684");
        assertEquals(inmate, new Inmate(123L, "20.211.213.157_vuln_seeking", 1691728931684L));
        assertEquals(inmate.getClientId(), "20.211.213.157_vuln_seeking");
        assertEquals(inmate.getReleaseTime(), 1691728931684L);
    }

    @Test
    public void test_BrigDisabled() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("IS_THE_BRIG_ENABLED", "false");
        var disabledBrigContext = buildTestingContext("testing brig disabled", properties);

        FullSystem fullSystem = new FullSystem(disabledBrigContext);
        fullSystem.start();

        assertTrue(fullSystem.getTheBrig() == null, "When the brig is disabled, it isn't initialized in FullSystem");

        shutdownTestingContext(disabledBrigContext);
        Files.deleteIfExists(Path.of("SYSTEM_RUNNING"));
    }

    /**
     * I encountered an issue where, after removing some locks from the
     * brig's code, it appeared to be possible to add multiple of the same
     * client id to the brig.  Not a great outcome.  The exception thrown
     * started like this:
     *
     * <pre>
     * Exception caught in WebFramework.finalExceptionHandler:
     * com.renomad.minum.database.DbException: More than one item found when
     * searching database Db<Inmate> on index "client_identifier_index" with
     * key 123.123.123.123_vuln_seeking...
     * </pre>
     *
     * And which pointed to the {@link TheBrig#isInJail(String)} method as
     * the culprit.  For that to happen, multiple threads must have ended
     * up putting the same ip address in.
     *
     * For this test, I will temporarily remove the locks and see that issue
     * manifest, and then replace the locks and expect it not to occur.
     */
    @Test
    public void test_MultiThreadedIssues() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        MyThread.sleep(50);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        TheBrig b = new TheBrig(10, context).initialize();
        // give the database time to start
        MyThread.sleep(20);


        int countOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(countOfThreads);

        // CyclicBarrier is a async-thread tool that we'll use to cause all our threads
        // to line up together at the starting line, as it were. This will maximize
        // our chances of catching a race condition.
        CyclicBarrier barrier = new CyclicBarrier(countOfThreads);

        // collect the futures of each thread to manage it later.
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < countOfThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // this await command will unlock once all 20 threads are awaiting,
                    // so they all get released to move forward at the exact same time,
                    // which will be likely to cause a race condition if one exists.
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
                b.sendToJail("foo", 100_000);
            }));
        }

        // wait here for all our threads to finish.
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        // Now, the test.  If our locks protected against multi-threaded
        // race conditions, then our method here won't throw an exception.
        // If it *does* fail, it will be because it found multiple foo's
        // in the database when our code should have prevented that.
        assertTrue(b.isInJail("foo"));

        b.stop();
    }
}
