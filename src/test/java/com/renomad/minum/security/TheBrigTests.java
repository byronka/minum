package com.renomad.minum.security;

import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

public class TheBrigTests {

    private static Context context;
    private static TestLogger logger;
    private static FileUtils fileUtils;

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

    /**
     * A user should be able to put a particular address in jail for
     * a time and after it has paid its dues, be released.
     */
    @Test
    public void test_TheBrig_Basic() {
        MyThread.sleep(50);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        var b = new TheBrig(10, context).initialize();
        // give the database time to start
        MyThread.sleep(20);

        // send some clients to jail
        ITheBrig finalB = b;
        Thread.ofVirtual().start(() -> finalB.sendToJail("1.1.1.1_too_freq_downloads", 10000));
        Thread.ofVirtual().start(() -> finalB.sendToJail("2.2.2.2_too_freq_downloads", 20));
        Thread.ofVirtual().start(() -> finalB.sendToJail("3.3.3.3_too_freq_downloads", 20));
        Thread.ofVirtual().start(() -> finalB.sendToJail("4.4.4.4_too_freq_downloads", 20));

        // what's the situation?
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("1.1.1.1_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("2.2.2.2_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("3.3.3.3_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("4.4.4.4_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertFalse(finalB.isInJail("DOES_NOT_EXIST")));

        MyThread.sleep(10);

        // after a short time, they should all still be in jail
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("1.1.1.1_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("2.2.2.2_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("3.3.3.3_too_freq_downloads")));
        Thread.ofVirtual().start(() -> assertTrue(finalB.isInJail("4.4.4.4_too_freq_downloads")));

        MyThread.sleep(80);

        b.stop();
        b = new TheBrig(10, context).initialize();
        MyThread.sleep(30);

        // after their release time, they should all be out, except 1.1.1.1, who bugged us
        assertTrue(b.isInJail("1.1.1.1_too_freq_downloads"));
        assertFalse(b.isInJail("2.2.2.2_too_freq_downloads"));
        assertFalse(b.isInJail("3.3.3.3_too_freq_downloads"));
        assertFalse(b.isInJail("4.4.4.4_too_freq_downloads"));

        // 1.1.1.1 bugged us some more.
        b.sendToJail("1.1.1.1_too_freq_downloads", 40);

        b.stop();
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
    }

    @Test
    public void test_TheBrig_RegularStop() {
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
    public void test_TheBrig_Uninitialized() {
        var b = new TheBrig(10, context);
        var ex = assertThrows(MinumSecurityException.class, b::stop);
        assertEquals(ex.getMessage(), "TheBrig was told to stop, but it was uninitialized");
    }

    /**
     * If an inmate was in the brig already and they transgressed again,
     * simply put them back in with a duration starting from the moment
     * of the new transgression.
     */
    @Test
    public void test_TheBrig_ExistingInmate() {
        MyThread.sleep(50);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(context.getConstants().dbDirectory));
        var b = new TheBrig(10, context).initialize();
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        Long releaseTime = b.getInmates().getFirst().getReleaseTime();
        assertTrue(b.isInJail("1.2.3.4_too_freq_downloads"));
        assertEquals(b.getInmates().size(), 1);
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        assertEquals(releaseTime + 20, b.getInmates().getFirst().getReleaseTime());
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
    public void test_BrigDisabled() {
        Properties properties = new Properties();
        properties.setProperty("IS_THE_BRIG_ENABLED", "false");
        var disabledBrigContext = buildTestingContext("testing brig disabled", properties);

        var theBrig = new TheBrig(10, disabledBrigContext);

        assertFalse(theBrig.sendToJail("", 0));
        assertFalse(theBrig.isInJail(""));
        shutdownTestingContext(disabledBrigContext);
    }
}
