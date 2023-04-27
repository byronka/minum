package atqa.utils;

/**
 * This class provides some tools for running a virtual stopwatch
 * while code is running, to examine code speed.
 *
 * <pre>
 *     {@code
 *     example:
 *     final var timer = new StopWatch().startTimer();
 *     for (var i = 1; i < 5; i++) {
 *         doStuff();
 *     }
 *     final var time = timer.stopTimer();
 *     printf("time taken was " + time " + milliseconds");
 *     }
 * </pre>
 */
public class StopwatchUtils {

    private long startTime = 0;

    public StopwatchUtils startTimer() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    public StopwatchUtils() {

    }


    public long stopTimer() {
        final var endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
}
