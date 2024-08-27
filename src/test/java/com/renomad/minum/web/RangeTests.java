package com.renomad.minum.web;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import java.util.List;

import static com.renomad.minum.testing.TestFramework.*;

public class RangeTests {

    @Test
    public void testEquals() {
        EqualsVerifier.forClass(Range.class).verify();
    }

    @Test
    public void test_DetermineLengthFromRangeHeader() {
        List<String> listOfHeaders = List.of("Range: bytes=0-499");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 500L);
        assertEquals(range.getOffset(), 0L);
        assertEquals(range.getRangeFirstPart(), 0L);
        assertEquals(range.getRangeSecondPart(), 499L);
        assertTrue(range.hasRangeHeader());
        assertEquals(range.toString(), "Range{rangeFirstPart=0, rangeSecondPart=499, length=500, offset=0, hasRangeHeader=true}");
    }

    @Test
    public void test_DetermineLengthFromRangeHeader_EdgeCase_MissingFirstPart() {
        List<String> listOfHeaders = List.of("Range: bytes=-499");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 499L);
        assertEquals(range.getOffset(), 501L);
        assertTrue(range.getRangeFirstPart() == null);
        assertEquals(range.getRangeSecondPart(), 499L);
    }

    @Test
    public void test_DetermineLengthFromRangeHeader_EdgeCase_MissingSecondPart() {
        List<String> listOfHeaders = List.of("Range: bytes=2-");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 998L);
        assertEquals(range.getOffset(), 2L);
        assertEquals(range.getRangeFirstPart(), 2L);
        assertTrue(range.getRangeSecondPart() == null);
    }

    @Test
    public void test_DetermineLengthFromRangeHeader_EdgeCase_MissingSecondPart_2() {
        List<String> listOfHeaders = List.of("Range: bytes=0-");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertEquals(range.getRangeFirstPart(), 0L);
        assertTrue(range.getRangeSecondPart() == null);
    }

    @Test
    public void test_DetermineLengthFromRangeHeader_EdgeCase_MissingBothParts() {
        List<String> listOfHeaders = List.of("Range: bytes=-");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    /**
     * The provided range should be SMALLER-LARGER.  If that is untrue, throw an exception.
     */
    @Test
    public void test_DetermineLengthFromRangeHeader_EdgeCase_FirstLarger() {
        List<String> listOfHeaders = List.of("Range: bytes=2-1");
        Headers headers = new Headers(listOfHeaders);
        var ex = assertThrows(InvalidRangeException.class, () -> new Range(headers, 1000));
        assertEquals(ex.getMessage(), "Error: The value of the first part of the range was larger than the second.");
    }

    /**
     * The provided range won't parse negative numbers.
     */
    @Test
    public void test_DetermineLengthFromRangeHeader_EdgeCase_NegativeNumbers() {
        List<String> listOfHeaders = List.of("Range: bytes=-2-");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    @Test
    public void test_InvalidValueForLeft() {
        List<String> listOfHeaders = List.of("Range: bytes=a-1");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    @Test
    public void test_InvalidValueForRight() {
        List<String> listOfHeaders = List.of("Range: bytes=1-a");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    @Test
    public void test_InvalidPattern() {
        List<String> listOfHeaders = List.of("Range: foo foo");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    /**
     * The regular expression will only allow up to a certain number
     * of digits or it won't find anything.
     */
    @Test
    public void test_InvalidPattern_tooLongLeft() {
        List<String> listOfHeaders = List.of("Range: 12345678911111-12");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    /**
     * The regular expression will only allow up to a certain number
     * of digits or it won't find anything.
     */
    @Test
    public void test_InvalidPattern_tooLongRight() {
        List<String> listOfHeaders = List.of("Range: 0-12345678911111");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }

    /**
     * There must be just one range header, or else it's invalid
     */
    @Test
    public void test_MultipleRangeHeaders() {
        List<String> listOfHeaders = List.of("Range: 1-2", "Range: 3-4");
        Headers headers = new Headers(listOfHeaders);
        var ex = assertThrows(InvalidRangeException.class, () -> new Range(headers, 1000));
        assertEquals(ex.getMessage(), "Error: Request contained more than one Range header");
    }

    /**
     * If there's no range header we return the whole content
     */
    @Test
    public void test_NoRange() {
        List<String> listOfHeaders = List.of();
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
        assertFalse(range.hasRangeHeader());
        assertEquals(range.toString(), "Range{rangeFirstPart=null, rangeSecondPart=null, length=1000, offset=0, hasRangeHeader=false}");
    }

    /**
     * Our system does not handle multiple ranges, so if that is
     * requested, we'll just return the whole content
     */
    @Test
    public void test_MultipleRange() {
        List<String> listOfHeaders = List.of("Range: 1-2,2-3");
        Headers headers = new Headers(listOfHeaders);
        Range range = new Range(headers, 1000);
        assertEquals(range.getLength(), 1000L);
        assertEquals(range.getOffset(), 0L);
        assertTrue(range.getRangeFirstPart() == null);
        assertTrue(range.getRangeSecondPart() == null);
    }
}
