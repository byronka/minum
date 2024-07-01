package com.renomad.minum.utils;

import com.renomad.minum.security.ForbiddenUseException;
import org.junit.Test;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

public class SerializationUtilsTests {

    @Test
    public void testSerializationHelper() {
        String a = "a";
        String b = "b";
        String c = null;
        String d = null;
        String result = SerializationUtils.serializeHelper(a, b, c, d);
        assertEquals(result, "a|b|%NULL%|%NULL%");
    }

    @Test
    public void testTokenizer() {
        List<String> tokens = SerializationUtils.tokenizer("a|b|%NULL%|%NULL%", '|', 100);
        assertEquals(tokens.size(), 4);
        assertEquals(tokens.get(0), "a");
        assertEquals(tokens.get(1), "b");
        assertEquals(tokens.get(2), "%NULL%");
        assertEquals(tokens.get(3), "%NULL%");
    }

    @Test
    public void testTokenizer_OverMaxTokenCount() {
        // first two should run fine...
        assertEquals(SerializationUtils.tokenizer("a", '|', 2).size(), 1);
        assertEquals(SerializationUtils.tokenizer("a|b", '|', 2).size(), 2);

        // third and further tokens should throw exceptions...
        var ex = assertThrows(ForbiddenUseException.class, () -> SerializationUtils.tokenizer("a|b|%NULL%", '|', 2));
        assertEquals(ex.getMessage(), "Asked to split content into too many partitions in the tokenizer.  Current max: 2" );
        assertThrows(ForbiddenUseException.class, () -> SerializationUtils.tokenizer("a|b|%NULL%|%NULL%", '|', 2));
    }
}
