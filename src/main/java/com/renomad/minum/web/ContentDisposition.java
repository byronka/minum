package com.renomad.minum.web;

import java.util.Objects;

/**
 * This class represents the information in the Content-Disposition
 * header of a multipart/form-data partition.  Let's look at an example:
 *
 *  <pre>
 *  --i_am_a_boundary
 *   Content-Type: text/plain
 *   Content-Disposition: form-data; name="text1"
 *
 *   I am a value that is text
 *   --i_am_a_boundary
 *   Content-Type: application/octet-stream
 *   Content-Disposition: form-data; name="image_uploads"; filename="photo_preview.jpg"
 *  </pre>
 *
 *  <br>
 *  In this example, there are two partitions, and each has a Content-Disposition header.
 *  The first has a name of "text1" and the second has a name of "image_uploads".  The
 *  second partition also has a filename.
 *  <br>
 *  This is useful for filtering partition data when an endpoint receives multipart data.
 *
 */
public final class ContentDisposition {
    private final String name;
    private final String filename;

    public ContentDisposition(String name, String filename) {

        this.name = name;
        this.filename = filename;
    }

    public String getName() {
        return name;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentDisposition that = (ContentDisposition) o;
        return Objects.equals(name, that.name) && Objects.equals(filename, that.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, filename);
    }

    @Override
    public String toString() {
        return "ContentDisposition{" +
                "name='" + name + '\'' +
                ", filename='" + filename + '\'' +
                '}';
    }
}
