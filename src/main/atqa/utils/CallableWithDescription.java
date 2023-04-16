package atqa.utils;

import java.util.concurrent.Callable;

/**
 * This class is to improve maintainability in the system.  It makes
 * possible reviewing the queue of actions and more easily understanding
 * the purpose of each Callable.
 */
public class CallableWithDescription implements Callable<Void> {

    private final String description;
    private final Callable<Void> c;

    public CallableWithDescription(Callable<Void> c, String description) {
        this.description = description;
        this.c = c;
    }

    @Override
    public Void call() throws Exception {
        return c.call();
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
