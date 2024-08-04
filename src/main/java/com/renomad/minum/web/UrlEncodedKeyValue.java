package com.renomad.minum.web;

import java.util.Objects;

/**
 * Represents a key-value pair with URL-encoding.
 * This is the format of data when the Request is sent with a
 * content-type header of application/x-www-form-urlencoded.
 */
public final class UrlEncodedKeyValue {
    private final String key;
    private final UrlEncodedDataGetter uedg;

    public UrlEncodedKeyValue(String key, UrlEncodedDataGetter uedg) {
        this.key = key;
        this.uedg = uedg;
    }

    public String getKey() {
        return key;
    }

    public UrlEncodedDataGetter getUedg() {
        return uedg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UrlEncodedKeyValue that = (UrlEncodedKeyValue) o;
        return Objects.equals(key, that.key) && Objects.equals(uedg, that.uedg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, uedg);
    }
}
