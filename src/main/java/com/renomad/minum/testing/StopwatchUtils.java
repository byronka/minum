package com.renomad.minum.testing;

/**
 * This class provides some tools for running a virtual stopwatch
 * while code is running, to examine code speed.
 * <h3>
 *     example:
 * </h3>
 *
 * <pre>
 {@code
 final var timer = new StopWatch().startTimer();
 for (var i = 1; i < 5; i++) {
     doStuff();
 }
 final var time = timer.stopTimer();
 printf("time taken was " + time " + milliseconds");
 }
 * </pre>
 */
public final class StopwatchUtils {

    private long startTime;

    public StopwatchUtils startTimer() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    public StopwatchUtils() {
        startTime = 0;
    }


    public long stopTimer() {
        final var endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
}
