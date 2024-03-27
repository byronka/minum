package com.renomad.minum.web;

import java.util.ArrayList;
import java.util.Objects;

import static com.renomad.minum.utils.Invariants.mustNotBeNull;

/**
 *  The Vary HTTP response header describes the parts of the request message aside from
 *  the method and URL that influenced the content of the response it occurs in. Most often,
 *  this is used to create a cache key when content negotiation is in use.
 *  <br>
 *  The same Vary header value should be used on all responses for a given
 *  URL, including 304 Not Modified responses and the "default" response.
 */
public final class VaryHeader {
    private final ArrayList<String> varyHeaders;

    public VaryHeader() {
        varyHeaders = new ArrayList<>();
    }

    /**
     * Add a new header from the request to the list that affected the response
     */
    public void addHeader(String header) {
        mustNotBeNull(header);
        varyHeaders.add(header);
    }

    /**
     * Get the current string value of this header
     */
    @Override
    public String toString() {
        return "Vary: " + String.join(",", varyHeaders);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VaryHeader that = (VaryHeader) o;
        return Objects.equals(varyHeaders, that.varyHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(varyHeaders);
    }
}
