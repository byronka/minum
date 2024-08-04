package com.renomad.minum.web;

/**
 * This exists so that we are able to better manage
 * exceptions in a highly threaded system.
 * <br>
 * Exceptions stop bubbling up at the thread invocation. If we
 * don't take care to deal with that in some way, we can easily
 * just lose the information.  Something could be badly broken and
 * we could be totally oblivious to it.  This interface is to
 * alleviate that situation.
 */
@FunctionalInterface
public interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;

}