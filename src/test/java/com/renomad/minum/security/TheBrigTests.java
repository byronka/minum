package com.renomad.minum.security;

import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.database.Db;
import com.renomad.minum.logging.TestLogger;
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
    private static Constants constants;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
        constants = context.getConstants();
        logger = (TestLogger) context.getLogger();
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
        var b = new TheBrig(10, context).initialize();
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        assertTrue(b.isInJail("1.2.3.4_too_freq_downloads"));
        MyThread.sleep(70);
        assertFalse(b.isInJail("1.2.3.4_too_freq_downloads"));
        b.stop();
    }

    @Test
    public void test_TheBrig_RegularStop() {
        var b = new TheBrig(10, context).initialize();
        MyThread.sleep(10);
        b.stop();
        assertTrue(true, "We should get here without an exception");
        assertTrue(logger.doesMessageExist("TheBrig has been told to stop"));
        assertTrue(logger.doesMessageExist("TheBrig: Sending interrupt to thread"));
        assertTrue(logger.doesMessageExist("TheBrig is stopped."));
    }

    /**
     * If the brig isn't initialized, there's no thread to stop
     */
    @Test
    public void test_TheBrig_Uninitialized() {
        var b = new TheBrig(10, context);
        var ex = assertThrows(SecurityException.class, b::stop);
        assertEquals(ex.getMessage(), "TheBrig was told to stop, but it was uninitialized");
    }

    /**
     * If an inmate was in the brig already and they transgressed again,
     * simply put them back in with a duration starting from the moment
     * of the new transgression.
     */
    @Test
    public void test_TheBrig_ExistingInmate() {
        var b = new TheBrig(10, context).initialize();
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        assertTrue(b.isInJail("1.2.3.4_too_freq_downloads"));
        assertEquals(b.getInmates().size(), 1);
        MyThread.sleep(10);
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        MyThread.sleep(10);
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        MyThread.sleep(10);
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
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

    /**
     * Every so often, the thread inside the brig wakes up and checks
     * on the "inmates".  These are strings which represent attackers,
     * along with their "release time" - the timestamp at which they can
     * be removed from the list.
     * <p>
     *     At the present, websites are under constant attack.  This is
     *     a simple tool to put some friction in the attacker's path.
     * </p>
     */
    @Test
    public void test_ProcessingInmateList() {
        Path dbDir = Path.of(constants.dbDirectory);
        context.getFileUtils().deleteDirectoryRecursivelyIfExists(dbDir.resolve("the_brig_tests"), logger);
        var inmatesDb = new Db<>(dbDir.resolve("the_brig_tests"), context, Inmate.EMPTY);
        inmatesDb.write(new Inmate(0L, "abc", 0L));
        inmatesDb.write(new Inmate(0L, "def", 99L));
        inmatesDb.write(new Inmate(0L, "ghi", 100L));
        inmatesDb.write(new Inmate(0L, "jkl", 101L));
        inmatesDb.write(new Inmate(0L, "mno", 200L));

        TheBrig.processInmateList(100L, inmatesDb.values(), logger, inmatesDb);

        assertEquals(inmatesDb.values().size(), 3);
        assertEquals(inmatesDb.values().toString(), "[Inmate{index=3, clientId='ghi', releaseTime=100}, Inmate{index=4, clientId='jkl', releaseTime=101}, Inmate{index=5, clientId='mno', releaseTime=200}]");
    }

    @Test
    public void test_BrigDisabled() {
        Properties properties = new Properties();
        properties.setProperty("IS_THE_BRIG_ENABLED", "false");
        var disabledBrigContext = buildTestingContext("testing brig disabled", properties);

        var constants = new Constants(properties);
        disabledBrigContext.setConstants(constants);
        var theBrig = new TheBrig(10, disabledBrigContext);

        assertFalse(theBrig.sendToJail("", 0));
        assertFalse(theBrig.isInJail(""));
        shutdownTestingContext(disabledBrigContext);
    }
}
