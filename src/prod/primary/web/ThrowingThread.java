package primary.web;

@FunctionalInterface
public interface ThrowingRunnable<T, E extends Exception>{

    void run() throws E;

    static Runnable throwingRunnableWrapper(ThrowingRunnable<T, Exception> throwingRunnable) {
        return _ -> {
            try {
                throwingRunnable.run();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }
//    void accept(T t) throws E;
//
//    static <T> Consumer<T> throwingConsumerWrapper(
//            ThrowingThread<T, Exception> throwingConsumer) {
//
//        return i -> {
//            try {
//                throwingConsumer.accept(i);
//            } catch (Exception ex) {
//                throw new RuntimeException(ex);
//            }
//        };
//    }
}