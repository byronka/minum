package atqa.utils;

@FunctionalInterface
public interface ThrowingRunnable<E extends Exception>{

    void run() throws E;

    static Runnable throwingRunnableWrapper(ThrowingRunnable<Exception> throwingRunnable) {
        return () -> {
            try {
                throwingRunnable.run();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }
}