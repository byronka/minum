package com.renomad.minum.logging;

import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.*;

public class LoggerTests {
    private Context context;
    private TestLogger logger;

    @Before
    public void init() {
        context = buildTestingContext("TestLogger tests");
        logger = (TestLogger) context.getLogger();
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    /**
     * This does print to output, but skips normal channels - just
     * uses a System.out.printf at the end.  We can't track it.
     */
    @Test
    public void testLogHelper() {
        Logger.logHelper(() -> "testing...", LoggingLevel.AUDIT, Map.of(LoggingLevel.AUDIT, true), null);
        var ex = assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("testing..."));
        assertTrue(ex.getMessage().contains("testing... was not found in"));
    }

    /**
     * In LogHelper, if we provide a map of logging levels that causes a message
     * not to be printed, then ... it does not print at all.
     */
    @Test
    public void testLogHelper_LoggingDisabled() {
        Logger.logHelper(() -> "testing...", LoggingLevel.AUDIT, Map.of(LoggingLevel.AUDIT, false), null);
        var ex = assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("testing..."));
        assertTrue(ex.getMessage().contains("testing... was not found in"));
    }

    /**
     * The {@link LoggingActionQueue} is what enables the log to send its
     * messages for later output.  It's critical.  But, there are situations
     * where the queue would not be available - primarily, 1) when the system
     * has just started up, and 2) When it's shutting down.  During those phases,
     * parts of the system are being spun up or shut down.
     * <br>
     * This tests where the LoggingActionQueue is stopped
     */
    @Test
    public void testLogHelper_EdgeCase_LoggingActionQueueStopping() {
        Properties props = new Properties();
        var constants = new Constants(props);
        var testQueue = new LoggingActionQueue("test queue", null, constants);
        testQueue.stop(0,0);
        Logger.logHelper(() -> "testing...", LoggingLevel.AUDIT, Map.of(LoggingLevel.AUDIT, true), testQueue);
        var ex = assertThrows(TestLoggerException.class, () -> logger.findFirstMessageThatContains("testing..."));
        assertTrue(ex.getMessage().contains("testing... was not found in"));
    }

    /**
     * When writing logs, if there is whitespace it is helpful to convert it
     * to a more apparent form, otherwise it could be overlooked. For example,
     * an entirely blank string is: (BLANK)
     */
    @Test
    public void testShowWhiteSpace() {
        assertEquals(Logger.showWhiteSpace(" "), "(BLANK)");
        assertEquals(Logger.showWhiteSpace(""), "(EMPTY)");
        assertEquals(Logger.showWhiteSpace(null), "(NULL)");
        assertEquals(Logger.showWhiteSpace("\t\r\n"), "\\t\\r\\n");
    }

    /**
     * This is a sample of code for enabling and disabling the TRACE
     * level of logging.
     */
    @Test
    public void testEnableAndDisableTrace() {
        logger.logTrace(() -> "You can't see me!");
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, true);
        logger.logTrace(() -> "But you can see this.");
        logger.getActiveLogLevels().put(LoggingLevel.TRACE, false);
        logger.logTrace(() -> "You can't see me!");
    }

    /**
     * Users may want to extend logger to add new logging levels
     * they can control.  Here is an example.
     */
    @Test
    public void testUsingDescendantLogger() {
        DescendantLogger descendantLogger = new DescendantLogger((Logger)context.getLogger());
        descendantLogger.logRequests(() -> "Incoming request from 123.123.123.123: foo foo");
        assertTrue((descendantLogger).doesMessageExist("Incoming request from 123.123.123.123: foo foo"));
    }
}
