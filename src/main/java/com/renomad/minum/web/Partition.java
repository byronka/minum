package com.renomad.minum.web;

import com.renomad.minum.utils.StringUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single partition in a multipart/form-data body response
 */
public final class Partition {

    private final Headers headers;
    private final byte[] content;
    private final ContentDisposition contentDisposition;

    public Partition(Headers headers, byte[] content, ContentDisposition contentDisposition) {
        this.headers = headers;
        this.content = content;
        this.contentDisposition = contentDisposition;
    }

    public Headers getHeaders() {
        return headers;
    }

    public ContentDisposition getContentDisposition() {
        return contentDisposition;
    }

    public byte[] getContent() {
        return content.clone();
    }
    public String getContentAsString() {
        return StringUtils.byteArrayToString(content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Partition partition = (Partition) o;
        return Objects.equals(headers, partition.headers) && Arrays.equals(content, partition.content) && Objects.equals(contentDisposition, partition.contentDisposition);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(headers, contentDisposition);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return "Partition{" +
                "headers=" + headers +
                ", contentDisposition=" + contentDisposition +
                '}';
    }
}
