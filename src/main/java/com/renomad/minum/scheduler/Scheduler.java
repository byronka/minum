package com.renomad.minum.scheduler;

import com.renomad.minum.database.Db;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.queue.ActionQueue;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.ThrowingRunnable;
import com.renomad.minum.utils.TimeUtils;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class starts an infinite loop when the application begins,
 * waiting until a time specified, to carry out a specified action.
 * <p>
 *     <em>
 *         Note that if the system is started after the scheduled time of a
 *         particular action that hasn't yet run, by default the action will be executed
 *         immediately.  If it is required that the scheduled action only execute
 *         around the scheduled time, pass an integer to {@link #addScheduledItem(ThrowingRunnable, LocalTime, String, long)}
 *         for maximum seconds past the scheduled time to be allowed.
 *     </em>
 * </p>
 * <p>
 *     The concept behind this scheduler is to write to the database after
 *     an action has taken place, and to scrap all those items in the database
 *     that take place in the future.
 * </p>
 * <p>
 *     The central loop wakes up each second to see whether
 *     there are any actions to take, and puts them on the {@link ActionQueue}
 *     if so, avoiding taking any time up in the Scheduler's own thread.
 * </p>
 * <pre>
 *     {@code
 *         Scheduler scheduler = new Scheduler(context);
 *         var result = scheduler.addScheduledItem(() -> System.out.println("hello world"), LocalTime.of(12, 45), "print hello world");
 *
 *         // At 12:45 pm, the action will run, printing "hello world".
 *         assertEquals(result.status, Scheduler.StatusEnum.COMPLETE);
 *     }
 * </pre>
 */
public class Scheduler {

    private final ExecutorService es;
    private final ILogger logger;
    private final Constants constants;
    private final List<ScheduledItem> scheduledItems;
    private final AtomicInteger scheduledItemIndex;
    private final ActionQueue actionQueue;
    /**
     * A lock to protect access to the list of scheduled items, to
     * avoid concurrent modification exceptions
     */
    private final Lock scheduledItemListLock = new ReentrantLock();

    /**
     * This is so we can control the current time, for testing
     */
    Callable<LocalTime> getNow;
    private final int sleepTime;
    private final Db<Schedule> schedule;
    private Thread myThread;

    /**
     * A record of an action to take place on a schedule
     *
     * @param index            assigned to this record by the {@link Scheduler}, for tracing between this and its
     *                         equivalent in the database
     * @param runnable         the action to run
     * @param time             the time of day this action should be run
     * @param status           the current status of this action
     * @param description      a description describing this action, to improve maintainability - meant for viewing
     *                         during debugging / logging.
     * @param maxTimeTolerance the maximum number of seconds past the scheduled time where the action will still be executed.
     *                         This may be expected to apply in situations where the system is first started late in the day.
     */
    record ScheduledItem(int index, ThrowingRunnable runnable, LocalTime time, CompletionStatus status, String description,
                         long maxTimeTolerance) {
    }

    /**
     *
     * @param context we get multiple pieces from this - the executor service, the logger, and the constants

     * @param getNow a {@link Callable} that will obtain the current time - used for testing
     * @param sleepTime the number of milliseconds to wait before checking again for whether it is time to do something,
     *                  mostly provided here for configuration for improving testing
     */
    public Scheduler(Context context, Callable<LocalTime> getNow, int sleepTime) {
        this.es = context.getExecutorService();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        schedule = context.getDb("minum_scheduler", Schedule.EMPTY);
        this.getNow = getNow;
        this.sleepTime = sleepTime;
        initialize();
        scheduledItems = new ArrayList<>();
        scheduledItemIndex = new AtomicInteger(0);
        actionQueue = new ActionQueue("Scheduler queue", context);
        actionQueue.initialize();
    }

    public Scheduler(Context context) {
        this(context, LocalTime::now, 1000);
    }

    public enum StatusEnum {
        PENDING,
        COMPLETE
    }

    public static class CompletionStatus {
        public StatusEnum status = StatusEnum.PENDING;
    }

    /**
     * add an item that will be run at a particular time
     * @param runnable the code that will get run at the specified time
     * @param time the time of day, local to the server, when the action will be run
     */
    public CompletionStatus addScheduledItem(ThrowingRunnable runnable, LocalTime time, String description) {
        // get the number of seconds from the specified time until midnight, so that no matter
        // how far we get from the specified time, the action will fall within the window of
        // allowable times to run (and will run immediately upon starting the central loop of this class)
        long secondsUntilMidnight = time.until(LocalTime.of(23,59,59,999), ChronoUnit.SECONDS);
        return addScheduledItem(runnable, time, description, secondsUntilMidnight);
    }

    /**
     * add an item that will be run at a particular time
     * @param runnable the code that will get run at the specified time
     * @param time the time of day, local to the server, when the action will be run
     * @param maxTimeTolerance the maximum amount of time, in seconds, beyond the scheduled time, this
     *                         action will be allowed to run.  This is handy for situations where the system
     *                         is started late in the day, where earlier scheduled actions would all
     *                         be executed at startup immediately without this.
     */
    public CompletionStatus addScheduledItem(ThrowingRunnable runnable, LocalTime time, String description, long maxTimeTolerance) {
        var scheduledItemCompletionStatus = new CompletionStatus();
        scheduledItemListLock.lock();
        try {
            scheduledItems.add(new ScheduledItem(scheduledItemIndex.getAndIncrement(), runnable, time, scheduledItemCompletionStatus, description, maxTimeTolerance));
        } finally {
            scheduledItemListLock.unlock();
        }
        return scheduledItemCompletionStatus;
    }

    @SuppressWarnings({"BusyWait"})
    private void initialize() {

        logger.logDebug(() -> "Initializing Scheduler main loop");
        Callable<Object> innerLoopThread = () -> {
            Thread.currentThread().setName("Scheduler");
            myThread = Thread.currentThread();
            while (true) {
                try {
                    actIfTime();
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    if (constants.logLevels.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " Scheduler is stopped.%n");
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception ex) {
                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: Scheduler has stopped unexpectedly. error: %s%n", ex);
                    throw ex;
                }
            }
        };
        es.submit(innerLoopThread);
    }

    /**
     * If the process determines it is time to act, the user-specified command will be run.
     */
    private void actIfTime() throws Exception {
        LocalTime now = getNow.call();

        // delete any future schedules from the database. Nothing should stay in the database
        // during the day until *after* we hit the time.
        schedule.values()
                .stream()
                .filter(x -> x.getTime().isAfter(now))
                .forEach(schedule::delete);


        List<ScheduledItem> pendingScheduledItems;
        scheduledItemListLock.lock();
        try {
            pendingScheduledItems = scheduledItems.stream().filter(x -> x.status().status.equals(StatusEnum.PENDING)).toList();
        } finally {
            scheduledItemListLock.unlock();
        }
        for (ScheduledItem si : pendingScheduledItems) {
            // boolean for whether this particular scheduled item has already been executed
            boolean thereAreNoDatabaseEntriesForThisItemAlready = schedule.values().stream().noneMatch(x -> x.getTime().isBefore(now) && x.getScheduledItemIndex() == si.index());
            // check whether we are within the window of time to run this scheduled item - after the scheduled time, before the end of the maximum time tolerance.
            LocalTime upperTimeWindow = si.time().plusSeconds(si.maxTimeTolerance());
            boolean isWithinTimeWindow = now.isAfter(si.time()) && now.isBefore(upperTimeWindow);
            logger.logTrace(() -> String.format("Scheduled item %s is within time window: %s to %s: %s", si.description, si.time, upperTimeWindow, isWithinTimeWindow));
            if (isWithinTimeWindow && thereAreNoDatabaseEntriesForThisItemAlready) {
                try {
                    logger.logDebug(() -> "Adding scheduled action to queue for running: " + si.description());
                    actionQueue.enqueue("scheduled action: " + si.description(), si.runnable());
                    schedule.write(new Schedule(0, si.index(), si.time()));
                    si.status.status = StatusEnum.COMPLETE;
                } catch (Exception e) {
                    logger.logAsyncError(() -> "Error occurred during run of action in scheduler: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
        }

    }

    /**
     * Kills the infinite loop running inside this class.
     */
    public void stop() {
        logger.logDebug(() -> "Scheduler has been told to stop");
        for (int i = 0; i < 10; i++) {
            if (myThread != null) {
                logger.logDebug(() -> "Scheduler: Sending interrupt to thread");
                myThread.interrupt();
                return;
            } else {
                MyThread.sleep(20);
            }
        }
        throw new RuntimeException("Scheduler: Leaving without successfully stopping thread");
    }

}
