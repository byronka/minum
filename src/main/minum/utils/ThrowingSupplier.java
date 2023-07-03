package minum.utils;

import minum.logging.ILogger;

import java.util.function.Supplier;

/**
 * This exists so that we are able to slightly better manage
 * exceptions in a highly threaded system.  Here's the thing:
 * exceptions stop bubbling up at the thread invocation. If we
 * don't take care to deal with that in some way, we can easily
 * just lose the information.  Something could be badly broken and
 * we could be totally oblivious to it.  This interface is to
 * alleviate that situation.
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable>{

    T get() throws E;

}