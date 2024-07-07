package com.renomad.minum.utils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * Utilities for searching collections of data
 */
public final class SearchUtils {

    private SearchUtils() {
        // not meant to be instantiated
    }

    /**
     * This helper method will give you the one item in this list, or
     * null if there are none.  If there's more than 1, it will throw
     * an exception.  This is for those times when we absolutely expect
     * there to be just one of a thing in a database, like if we're searching
     * for Persons by id.
     * @param searchPredicate a {@link Predicate} run to search for an element in the stream.
     * @throws InvariantException if there are two or more results found
     */
    public static <T> T findExactlyOne(Stream<T> streamOfSomething, Predicate<? super T> searchPredicate) {
        return findExactlyOne(streamOfSomething, searchPredicate, () -> null);
    }

    /**
     * This is similar to {@link #findExactlyOne(Stream, Predicate)} except that you
     * can provide what gets returned if there are none found - so instead of
     * returning null, it can return something else.
     * <br>
     * The values will be pre-filtered to skip any null values.
     * <br>
     * @param alternate a {@link Callable} that will be run when no elements were found.
     * @param searchPredicate a {@link Predicate} run to search for an element in the stream.
     * @throws InvariantException if there are two or more results found
     */
    public static <T> T findExactlyOne(Stream<T> streamOfSomething, Predicate<? super T> searchPredicate, Callable<T> alternate) {
        List<T> listOfThings = streamOfSomething.filter(Objects::nonNull).filter(searchPredicate).toList();
        mustBeTrue(listOfThings.isEmpty() || listOfThings.size() == 1, "Must be zero or one of this thing, or it's a bug.  We found a size of " + listOfThings.size());
        if (listOfThings.isEmpty()) {
            T returnValue;
            try {
                returnValue = alternate.call();
            } catch (Exception ex) {
                throw new UtilsException(ex);
            }
            return returnValue;
        } else {
            return listOfThings.getFirst();
        }
    }
}
