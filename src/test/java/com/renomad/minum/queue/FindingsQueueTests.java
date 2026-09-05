package com.renomad.minum.queue;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * Deliberately-failing regression tests for defects found in the
 * {@code com.renomad.minum.queue} package.
 * <p>
 *     Each test in this class asserts the <em>correct</em> (desired) behavior,
 *     and therefore fails against the current code.
 * </p>
 */
public class FindingsQueueTests {

    private static Context context;
    private static TestLogger logger;

    @BeforeClass
    public static void init() {
        context = buildTestingContext("FindingsQueueTests");
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
     * FINDING 14: stopping an idle ActionQueue leaves {@code isStopped()} reporting false.
     * <p>
     *     Defect: {@code src/main/java/com/renomad/minum/queue/ActionQueue.java:126} -
     *     {@code if (queue.isEmpty()) return;} sits inside the wait loop and returns
     *     <em>before</em> {@code isStoppedStatus = true;} on line 130.  In other words the
     *     clean shutdown path - the queue drained, nothing left to do - is exactly the path
     *     that never records that the queue stopped.  {@code isStoppedStatus} is only ever set
     *     on the path where the queue is still not empty after all the retries, i.e. the
     *     failure path.
     *     <br>
     *     The consequence shows up in logging.  {@code Logger.logHelper}
     *     ({@code src/main/java/com/renomad/minum/logging/Logger.java:134}) chooses between
     *     printing directly and enqueuing based on {@code loggingActionQueue.isStopped()}.
     *     Because a cleanly-stopped queue still answers false, every log line emitted after
     *     shutdown takes the enqueue branch, where it is dropped rather than printed - so the
     *     log messages from the end of a shutdown, precisely the ones that matter when a
     *     shutdown goes wrong, are silently lost.
     *     <br>
     *     Note that {@code stop(0, 0)} hides the defect, because with a count of zero the loop
     *     body never runs and the assignment is reached.  Production code calls the no-argument
     *     {@code stop()}, which is {@code stop(5, 20L)}.
     * </p>
     * <p>
     *     Correct behavior: after {@code stop()} returns, {@code isStopped()} must report true,
     *     whether the queue drained cleanly or was crashed closed.
     * </p>
     */
    @Test
    public void test_Finding_StopSetsStoppedStatusOnCleanPath() {
        var actionQueue = new ActionQueue("FindingsQueueTests action queue", context).initialize();

        // the queue is idle, so this is the clean shutdown path
        actionQueue.stop();

        assertTrue(actionQueue.isStopped(),
                "after stop() returns on an idle (already drained) ActionQueue, isStopped() must report true.  " +
                        "It reported false, because the 'if (queue.isEmpty()) return;' guard inside the wait loop " +
                        "returns before isStoppedStatus is assigned.  Logger.logHelper keys off isStopped(), so a " +
                        "false answer here means every log line emitted after shutdown is enqueued onto a dead queue " +
                        "instead of being printed.");
    }
}
