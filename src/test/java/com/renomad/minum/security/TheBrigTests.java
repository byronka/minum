package com.renomad.minum.security;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.MyThread;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.*;

public class TheBrigTests {

    private static Context context;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("unit_tests");
    }

    /**
     * A user should be able to put a particular address in jail for
     * a time and after it has paid its dues, be released.
     */
    @Test
    public void test_TheBrig_Basic() {
        var b = new TheBrig(10, context);
        b.initialize();
        b.sendToJail("1.2.3.4_too_freq_downloads", 20);
        assertTrue(b.isInJail("1.2.3.4_too_freq_downloads"));
        MyThread.sleep(70);
        assertFalse(b.isInJail("1.2.3.4_too_freq_downloads"));
        b.stop();
    }
}
