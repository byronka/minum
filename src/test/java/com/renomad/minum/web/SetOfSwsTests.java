package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.utils.ConcurrentSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.renomad.minum.testing.TestFramework.*;

public class SetOfSwsTests {

    private Context context;
    private TestLogger logger;

    @Before
    public void init() {
        context = buildTestingContext("testing SetOfSws");
        logger = (TestLogger) context.getLogger();
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void test_getSocketWrapperByRemoteAddr_Missing() {
        SetOfSws mySetOfSws = new SetOfSws(new ConcurrentSet<>(), logger, "Test server");
        assertThrows(RuntimeException.class, () -> mySetOfSws.getSocketWrapperByRemoteAddr("123.123.123.123", 1234));
    }
}
