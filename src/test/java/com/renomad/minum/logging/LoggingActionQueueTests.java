package com.renomad.minum.logging;

import com.renomad.minum.state.Context;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.RunnableWithDescription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * These are tests for the background processor for the logging
 * system.  It's particularly difficult to test because the normal
 * facilities for testing are unavailable.
 */
public class LoggingActionQueueTests {

    private Context context;

    @Before
    public void init() {
        context = buildTestingContext("TestLogger tests");
    }

    @After
    public void cleanup() {
        shutdownTestingContext(context);
    }

    @Test
    public void testGetQueueThread() {
        var testQueue = new LoggingActionQueue("my test queue", context.getExecutorService(), context.getConstants());
        testQueue.initialize();
        MyThread.sleep(10);
        assertEquals(testQueue.getQueueThread().getName(), "my test queue");
    }

    @Test
    public void testGetQueue() {
        var testQueue = new LoggingActionQueue("my test queue", context.getExecutorService(), context.getConstants());
        testQueue.initialize();
        testQueue.enqueue("Printing a test comment", () -> {MyThread.sleep(20);System.out.println("This is a test");});
        testQueue.enqueue("Printing a test comment", () -> {MyThread.sleep(20);System.out.println("This is a test");});

        assertEquals(testQueue.getQueue().peek().toString(), "Printing a test comment");
    }

    @Test
    public void testErrorWhileRunningAction() throws InterruptedException {
        LinkedBlockingQueue<RunnableWithDescription> foo = new LinkedBlockingQueue<>();
        foo.add(new RunnableWithDescription(() -> {
            throw new RuntimeException("This is a test exception");
        }, "Testing runAction"));
        assertFalse(LoggingActionQueue.runAction(foo));
    }
    
    @Test
    public void testHappyPathWhileRunningAction() throws InterruptedException {
        LinkedBlockingQueue<RunnableWithDescription> foo = new LinkedBlockingQueue<>();
        foo.add(new RunnableWithDescription(() -> System.out.println("hello world"), "Testing runAction"));
        assertTrue(LoggingActionQueue.runAction(foo));
    }

}
