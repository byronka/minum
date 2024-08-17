package com.renomad.minum.web;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Range {

    private static final Pattern rangeHeaderPattern = Pattern.compile("^bytes=(?<first>[0-9]{0,13})-(?<second>[0-9]{0,13})$");
    private final Long rangeFirstPart;
    private final Long rangeSecondPart;
    private final Long length;
    private final Long offset;
    private final boolean hasRangeHeader;

    public Range(Headers requestHeaders, long fullLength) {
        List<String> rangeHeaders = requestHeaders.valueByKey("range");
        if (rangeHeaders == null) {
            hasRangeHeader = false;
            rangeFirstPart = null;
            rangeSecondPart = null;
            length = fullLength;
            offset = 0L;
        } else {
            hasRangeHeader = true;
            if (rangeHeaders.size() > 1) {
                throw new InvalidRangeException("Error: Request contained more than one Range header");
            }
            // the "Range:" header provides a desired range, and can request multiple ranges.
            // this server does not currently handle multiple ranges, so if that is requested we
            // will ignore the range header and return a 200 with the entire contents.
            Matcher matcher = rangeHeaderPattern.matcher(rangeHeaders.getFirst());
            if (matcher.matches()) {
                String firstPart = matcher.group("first");
                String secondPart = matcher.group("second");

                if (!firstPart.isEmpty()) {
                    rangeFirstPart = Long.parseLong(firstPart);
                } else {
                    rangeFirstPart = null;
                }

                if (!secondPart.isEmpty()) {
                    rangeSecondPart = Long.parseLong(secondPart);
                } else {
                    rangeSecondPart = null;
                }

                // options
                // 1: there's a first and second part
                // 2: only a second part
                // 3: only a first part
                // 4: (invalid) the first part is larger than the second part
                // 5: (invalid) either of the range values are invalid longs
                if (rangeFirstPart != null && rangeSecondPart != null) {
                    if (rangeFirstPart > rangeSecondPart) {
                        throw new InvalidRangeException("Error: The value of the first part of the range was larger than the second.");
                    } else {
                        length = (rangeSecondPart - rangeFirstPart) + 1;
                        offset = rangeFirstPart;
                    }
                } else if (rangeFirstPart != null) {
                    offset = rangeFirstPart;
                    length = fullLength - offset;
                } else if (rangeSecondPart != null) {
                    offset = fullLength - rangeSecondPart;
                    length = rangeSecondPart;
                } else {
                    length = fullLength;
                    offset = 0L;
                }

            } else {
                rangeFirstPart = null;
                rangeSecondPart = null;
                length = fullLength;
                offset = 0L;
            }
        }
    }

    public Long getRangeFirstPart() {
        return rangeFirstPart;
    }

    public Long getRangeSecondPart() {
        return rangeSecondPart;
    }

    public Long getLength() {
        return length;
    }

    public Long getOffset() {
        return offset;
    }

    public boolean hasRangeHeader() {
        return hasRangeHeader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return hasRangeHeader == range.hasRangeHeader && Objects.equals(rangeFirstPart, range.rangeFirstPart) && Objects.equals(rangeSecondPart, range.rangeSecondPart) && Objects.equals(length, range.length) && Objects.equals(offset, range.offset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rangeFirstPart, rangeSecondPart, length, offset, hasRangeHeader);
    }

    @Override
    public String toString() {
        return "Range{" +
                "rangeFirstPart=" + rangeFirstPart +
                ", rangeSecondPart=" + rangeSecondPart +
                ", length=" + length +
                ", offset=" + offset +
                ", hasRangeHeader=" + hasRangeHeader +
                '}';
    }
}