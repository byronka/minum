package minum.testing;

/**
 * This class provides some tools for running a virtual stopwatch
 * while code is running, to examine code speed.
 *
 * <pre>
 *     {@code
 *     example:
 *     final var timer = new StopWatch();
 *     for (var i = 1; i < 5; i++) {
 *         doStuff();
 *     }
 *     final var time = timer.stopTimer();
 *     printf("time taken was " + time " + milliseconds");
 *     }
 * </pre>
 */
public class StopWatch {

    private long startTime;

    public StopWatch() {
        this.startTime = System.currentTimeMillis();
    }


    public long stopTimer() {
        final var endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
}
