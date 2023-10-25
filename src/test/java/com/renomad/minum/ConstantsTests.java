package com.renomad.minum;

import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static com.renomad.minum.testing.TestFramework.assertEquals;

/**
 * The {@link Constants} class is where we store values that
 * should not change during the course of the program. You
 * might think that means it can be static, and you would
 * be right.  However, by making it an object, it does
 * give us some leeway in our testing - it frees us to
 * have multiple instances of the whole program running
 * with potentially varying constants.  That kind of
 * flexibility is not possible without making nearly
 * everything an instantiable class.
 */
public class ConstantsTests {

    /**
     * There are a few "getProps" methods in the
     * Constants class, which are there to enable
     * converting strings to more sophisticated
     * types.
     * <br>
     * One in particular is the overload that returns
     * a list of strings.  Here, we'll test that it
     * behaves as expected.
     */
    @Test
    public void testGetProps_Array() {
        List<String> extraMimeMappings = Constants.extractList(" a,b, c, d, foo bar   , biz", "");
        assertEquals(extraMimeMappings, List.of("a","b","c","d", "foo bar","biz"));
    }
}
