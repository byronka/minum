package atqa.primary;

public class StopWatch {

    private long startTime = 0;

    public StopWatch startTimer() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    public StopWatch() {

    }


    public long stopTimer() {
        final var endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
}
