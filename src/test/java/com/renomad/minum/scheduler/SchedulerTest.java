package com.renomad.minum.scheduler;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.FileUtils;
import com.renomad.minum.utils.MyThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.concurrent.Callable;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;


public class SchedulerTest
{

    private Context context;
    private TestLogger logger;

    @Before
    public void init() {
        context = buildTestingContext("SchedulerTests");
        logger = (TestLogger)context.getLogger();
    }

    @After
    public void cleanup() {
        // delay a sec so our system has time to finish before we start deleting files
        MyThread.sleep(500);
        context.getLogger().stop();
        context.getExecutorService().shutdownNow();
        Constants constants = context.getConstants();
        FileUtils fileUtils = new FileUtils(context.getLogger(), constants);
        fileUtils.deleteDirectoryRecursivelyIfExists(Path.of(constants.dbDirectory).resolve("schedule"));
    }

    @Test
    public void testThatActionShouldNotRunIfBeforeTime() {
        Callable<LocalTime> currentTime = () -> LocalTime.of(12, 44);
        Scheduler scheduler = new Scheduler(context, currentTime, 30);
        var status = scheduler.addScheduledItem(() -> System.out.println("hello world"), LocalTime.of(12, 45), "print hello world");
        // wait to give time for the system to decide not to take action
        MyThread.sleep(100);
        assertEquals(status.status, Scheduler.StatusEnum.PENDING);
    }

    /**
     * If we specify a maximum time tolerance, then an action won't take place
     * if the first opportunity is too far past the time.
     */
    @Test
    public void testThatActionShouldNotRunIfAfterTimeWithShortTolerance() {
        Callable<LocalTime> currentTime = () -> LocalTime.of(12, 44);
        Scheduler scheduler = new Scheduler(context, currentTime, 30);
        var status = scheduler.addScheduledItem(() -> System.out.println("hello world"), LocalTime.of(12, 34), "print hello world", 10);
        // wait to give time for the system to decide not to take action
        MyThread.sleep(100);
        assertEquals(status.status, Scheduler.StatusEnum.PENDING);
    }

    @Test
    public void testThatActionShouldRunIfAfterTime() {
        Callable<LocalTime> currentTime = () -> LocalTime.of(12, 45, 15);
        Scheduler scheduler = new Scheduler(context, currentTime, 50);
        var status1 = scheduler.addScheduledItem(() -> System.out.println("hello world 1"), LocalTime.of(12, 45, 14), "print hello world part 1");
        var status2 = scheduler.addScheduledItem(() -> System.out.println("hello world 2"), LocalTime.of(12, 45, 15), "print hello world part 2");
        // wait for the action to take place.
        MyThread.sleep(100);
        assertEquals(status1.status, Scheduler.StatusEnum.COMPLETE);
        assertEquals(status2.status, Scheduler.StatusEnum.PENDING);
        scheduler.getNow = () -> LocalTime.of(12,45,16);
        MyThread.sleep(100);
        assertEquals(status2.status, Scheduler.StatusEnum.COMPLETE);
        // a bit of time to see the output in the test logs
        MyThread.sleep(100);
    }

    /**
     * If the action was supposed to take place a long while ago, it should
     * run immediately (unless the user specified a short time tolerance)
     */
    @Test
    public void testThatActionShouldRunIfLongAfterTime() {
        Callable<LocalTime> currentTime = () -> LocalTime.of(23, 45, 15);
        Scheduler scheduler = new Scheduler(context, currentTime, 50);
        var status1 = scheduler.addScheduledItem(() -> System.out.println("hello world abc"), LocalTime.of(12, 45, 14), "print hello world part abc");
        // wait for the action to take place.
        MyThread.sleep(100);
        assertEquals(status1.status, Scheduler.StatusEnum.COMPLETE);
        scheduler.getNow = () -> LocalTime.of(12,45,16);
        // a bit of time to see the output in the test logs
        MyThread.sleep(100);
    }


}
