package com.renomad.minum.utils;

import com.renomad.minum.sampledomain.auth.User;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;
import static com.renomad.minum.utils.SearchUtils.findExactlyOne;

public class SearchUtilsTests {

    /**
     * It's a common situation to search a collection and expect to either
     * find one or none, and anything else represents a bug.  For example,
     * if I am searching a list of users by id, I should either find the user
     * or not - but finding two or three would indicate a bug.  This helper
     * is to cut down on needing to write that code over and over.
     */
    @Test
    public void test_SearchUtils_OneOrNone() {
        List<String> items = List.of("a", "b", "c");

        // if we find it, return it
        String b = findExactlyOne(items.stream(), x -> x.equals("b"));
        assertEquals(b, "b");

        // if we don't find anything, return null
        String d = findExactlyOne(items.stream(), x -> x.equals("d"));
        assertTrue(d == null);
    }

    /**
     * If there are duplicates, throw an exception
     */
    @Test
    public void test_SearchUtils_OneOrNone_Duplicates() {
        // a list with two identical elements
        List<String> items = List.of("a", "b", "b");

        // when finding "b", our method throws an exception because there are 2.
        var ex = assertThrows(InvariantException.class, () -> findExactlyOne(items.stream(), x -> x.equals("b")));
        assertEquals(ex.getMessage(), "Must be zero or one of this thing, or it's a bug.  We found a size of 2");
    }

    /**
     * By default the findExactlyOne method returns null if none are found, but
     * if you are using a null object pattern, you can specify something else to
     * be returned.  In fact, you specify a {@link java.util.concurrent.Callable},
     * which is run at the point in code where it would have returned null.  You have
     * some nice options.
     */
    @Test
    public void test_SearchUtils_OneOrNone_SpecifyReturnValue() {
        // an empty list.
        List<User> users = List.of();

        // when we search the empty list and find nothing, we can specify an empty object to return.
        User result = findExactlyOne(
                users.stream(),
                user -> user.getId() == 1,
                () -> User.EMPTY);

        assertEquals(result, User.EMPTY);
    }

    /**
     * What should happen if the only item in the list is a null value?
     * Same as when it does not find anything.
     */
    @Test
    public void test_SearchUtils_OnlyNullInList() {
        List<String> items = new ArrayList<>();
        items.add(null);

        // if we find it, return it
        String b = findExactlyOne(items.stream(), x -> x.equals("b"));
        assertTrue(b == null);
    }

    /**
     * What should happen if the only item in the list is a null value?
     * Same as when it does not find anything. With alternate.
     */
    @Test
    public void test_SearchUtils_OnlyNullInList_WithAlternate() {
        List<String> items = new ArrayList<>();
        items.add(null);

        // if we find it, return it
        String b = findExactlyOne(items.stream(), x -> x.equals("b"), () -> "foo");
        assertEquals(b, "foo");
    }

    /**
     * What if the alternate throws an exception?
     */
    @Test
    public void test_SearchUtils_OnlyNullInList_WithAlternateException() {
        List<String> items = new ArrayList<>();
        items.add(null);

        // if we find it, return it
        var ex = assertThrows(UtilsException.class, () -> {
            findExactlyOne(items.stream(), x -> x.equals("b"), () -> {
                throw new Exception("Just testing");
            });
        });

        assertEquals(ex.getMessage(), "java.lang.Exception: Just testing");
    }

    /**
     * What should happen if there are nulls in the list, but also
     * the item we want to find?
     */
    @Test
    public void test_SearchUtils_NullInList() {
        List<String> items = new ArrayList<>();
        items.add("a");
        items.add("b");
        items.add(null);

        // if we find it, return it
        String b = findExactlyOne(items.stream(), x -> x.equals("b"));
        assertEquals(b, "b");
    }
}
