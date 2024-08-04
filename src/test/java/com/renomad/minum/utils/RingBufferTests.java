package com.renomad.minum.utils;

import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.state.Context;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static com.renomad.minum.testing.TestFramework.*;

/**
 * We need a ring buffer to make some of our parsers fast enough.
 * See https://en.wikipedia.org/wiki/Circular_buffer for more explanation.
 */
public class RingBufferTests {

    private TestLogger logger;

    @Before
    public void init() {
        Context ctx = buildTestingContext("testing_ring_buffer");
        this.logger = (TestLogger)ctx.getLogger();
    }

    /**
     * Stepping through the basic behaviors of our {@link RingBuffer}
     */
    @Test
    public void test_RingBuffer_HappyPath() {
        var rb = new RingBuffer<>(4, Integer.class);

        // just one element in the buffer - the number 1.
        rb.add(1);
        compare(rb, setExpectedValues(null, null, null, 1));

        // The next item is the number 2.
        rb.add(2);
        compare(rb, setExpectedValues(null, null, 1, 2));

        // The next item is the number 3.
        rb.add(3);
        compare(rb, setExpectedValues(null, 1, 2, 3));

        // The next item is the number 4.
        rb.add(4);
        compare(rb, setExpectedValues(1, 2, 3, 4));

        // The next item is the number 5.  This one will overwrite the oldest element, 1.
        rb.add(5);
        compare(rb, setExpectedValues(2, 3, 4, 5));

        // The next item is the number 6.  This one will overwrite the second oldest element, 2.
        rb.add(6);
        compare(rb, setExpectedValues(3, 4, 5, 6));
    }

    /**
     * A helper method to set up expected results when the ringbuffer
     * has four slots.
     */
    private static List<Integer> setExpectedValues(Integer a, Integer b, Integer c, Integer d) {
        List<Integer> expected = new ArrayList<>();
        expected.add(a);
        expected.add(b);
        expected.add(c);
        expected.add(d);
        return expected;
    }

    /**
     * Using the {@link RingBuffer}'s iterator, loop through, comparing each value
     * to a list we provide of expected results.
     */
    private static void compare(RingBuffer<Integer> rb, List<Integer> expected) {
        int index = 0;
        for (Integer i : rb) {
            Integer b = expected.get(index);
            assertTrue(Objects.equals(i, b), "at index " + index + ", the value was " + b);
            index += 1;
        }
    }

    /**
     * testing a utility to see whether we can find some data anywhere
     * inside the RingBuffer
     */
    @Test
    public void testContains() {
        RingBuffer<Character> characters = new RingBuffer<>(4, Character.class);
        characters.add('a');
        characters.add('b');
        characters.add('c');
        characters.add('d');

        assertTrue(characters.contains(List.of('a')));
        assertTrue(characters.contains(List.of('a', 'b')));
        assertTrue(characters.contains(List.of('a', 'b', 'c', 'd')));
        assertTrue(characters.contains(List.of('b', 'c', 'd')));
        assertTrue(characters.contains(List.of('c', 'd')));
        assertTrue(characters.contains(List.of('d')));
        assertFalse(characters.contains(List.of('e')));
        assertFalse(characters.contains(List.of('a', 'b', 'z')));

        var ex1 = assertThrows(UtilsException.class, () -> characters.contains(List.of()));
        assertEquals(ex1.getMessage(), "expected a valid non-empty list to search for in the RingBuffer");
        var ex2 = assertThrows(UtilsException.class, () -> characters.contains(null));
        assertEquals(ex2.getMessage(), "expected a valid non-empty list to search for in the RingBuffer");
    }

    /**
     * testing a utility to see whether we can find some data at a particular
     * place inside the RingBuffer
     */
    @Test
    public void testContainsAt() {
        RingBuffer<Character> characters = new RingBuffer<>(4, Character.class);
        characters.add('a');
        characters.add('b');
        characters.add('c');
        characters.add('d');

        assertTrue(characters.containsAt(List.of('a'), 0));
        assertTrue(characters.containsAt(List.of('b', 'c', 'd'), 1));
        assertFalse(characters.containsAt(List.of('b', 'c', 'd'), 0));
        assertTrue(characters.containsAt(List.of('c', 'd'), 2));
        assertTrue(characters.containsAt(List.of('d'), 3));
        assertFalse(characters.containsAt(List.of('e'), 3));
        assertFalse(characters.containsAt(List.of('d'), 0));
        var ex = assertThrows(UtilsException.class, () -> characters.containsAt(List.of(), 0));
        assertEquals(ex.getMessage(), "expected a valid non-empty list to search for in the RingBuffer");
        var ex2 = assertThrows(UtilsException.class, () -> characters.containsAt(null, 0));
        assertEquals(ex2.getMessage(), "expected a valid non-empty list to search for in the RingBuffer");
        var ex3 = assertThrows(UtilsException.class, () -> characters.containsAt(List.of('a'), -1));
        assertEquals(ex3.getMessage(), "expected an index greater than zero and less-than-or-equal to the last index of the buffer (the limit minus one)");
        var ex4 = assertThrows(UtilsException.class, () -> characters.containsAt(List.of('a'), 4));
        assertEquals(ex4.getMessage(), "expected an index greater than zero and less-than-or-equal to the last index of the buffer (the limit minus one)");
        var ex5 = assertThrows(UtilsException.class, () -> characters.containsAt(List.of('a'), 5));
        assertEquals(ex5.getMessage(), "expected an index greater than zero and less-than-or-equal to the last index of the buffer (the limit minus one)");
    }

    /**
     * If the iterator for the {@link RingBuffer} is asked to go past its
     * limit, it should return a {@link java.util.NoSuchElementException}
     */
    @Test
    public void testIteratingPastLimit() {
        RingBuffer<Integer> integers = new RingBuffer<>(3, Integer.class);
        integers.add(1);
        integers.add(2);
        integers.add(3);
        Iterator<Integer> iterator = integers.iterator();
        assertEquals(1, iterator.next());
        assertEquals(2, iterator.next());
        assertEquals(3, iterator.next());
        assertThrows(NoSuchElementException.class, iterator::next);
    }
}
