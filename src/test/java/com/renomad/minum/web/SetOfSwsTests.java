package com.renomad.minum.web;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.ConcurrentSet;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static com.renomad.minum.testing.TestFramework.*;

public class SetOfSwsTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("SetOfSwsTests");
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

    @Test
    public void test_SetOfSws() {
        ConcurrentSet<ISocketWrapper> mySet = new ConcurrentSet<>();
        SetOfSws mySetOfSws = new SetOfSws(mySet, logger, "my set");
        FakeSocketWrapper sw1 = new FakeSocketWrapper();
        mySetOfSws.add(sw1);
        assertTrue(logger.doesMessageExist("my set added fake socket wrapper to SetOfSws. size: 1"));
        FakeSocketWrapper sw2 = new FakeSocketWrapper();
        mySetOfSws.add(sw2);
        assertTrue(logger.doesMessageExist("my set added fake socket wrapper to SetOfSws. size: 2"));
        mySetOfSws.remove(sw1);
        assertTrue(logger.doesMessageExist("my set removed fake socket wrapper from SetOfSws. size: 1"));
        mySetOfSws.remove(sw2);
        assertTrue(logger.doesMessageExist("my set removed fake socket wrapper from SetOfSws. size: 0"));
    }

}
