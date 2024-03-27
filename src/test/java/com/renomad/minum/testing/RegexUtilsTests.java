package com.renomad.minum.testing;

import org.junit.Test;

import static com.renomad.minum.testing.RegexUtils.find;
import static com.renomad.minum.testing.RegexUtils.isFound;
import static com.renomad.minum.testing.TestFramework.*;

public class RegexUtilsTests {

    /**
     * We should be able to do a quick search by regex
     */
    @Test
    public void test_RegexUtils_QuickSearch() {
        var result = find("photo\\?name=[a-z0-9\\-]*", "src=photo?name=abc123");
        assertEquals(result, "photo?name=abc123");
    }

    /**
     * We should also have the option to search using a named matching group
     */
    @Test
    public void test_RegexUtils_SearchNamedMatchingGroup() {
        var result = find("photo\\?name=(?<somecoolname>[a-z0-9\\-]*)", "src=photo?name=abc123", "somecoolname");
        assertEquals(result, "abc123");
    }

    /**
     * The named matching group must be all alphanumerics - no special chars, please
     */
    @Test
    public void test_RegexUtils_SearchNamedMatchingGroup_NoSpecialChars() {
        var ex = assertThrows(java.util.regex.PatternSyntaxException.class, () -> find("photo\\?name=(?<some_cool_name>[a-z0-9\\-]*)", "src=photo?name=abc123", "some_cool_name"));
        assertTrue(ex.getMessage().contains("named capturing group is missing trailing '>' near index 19"));
    }

    @Test
    public void test_RegexUtils_NothingFound() {
        assertEquals(find("foo", "bar"), "");
        assertEquals(find("foo", "bar", "baz"), "");
    }

    @Test
    public void test_isFound() {
        assertTrue(isFound("foo", "foo"));
        assertFalse(isFound("bar", "foo"));
    }
}
