package com.renomad.minum.web;


import java.util.Arrays;
import java.util.Objects;

/**
 * This type is used to contain the results of modifications
 * applied to the outgoing data just before sending
 */
final class PreparedResponse {

    private final String statusLineAndHeaders;
    private final byte[] body;

    /**
     * Construct a {@link PreparedResponse}
     * @param statusLineAndHeaders string values separated by CRLF, per HTTP spec
     * @param body the body of the message, as bytes.  May be compressed.
     */
    PreparedResponse(String statusLineAndHeaders, byte[] body) {

        this.statusLineAndHeaders = statusLineAndHeaders;
        this.body = body.clone();
    }

    public String getStatusLineAndHeaders() {
        return statusLineAndHeaders;
    }

    public byte[] getBody() {
        return body.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PreparedResponse that)) return false;
        return Objects.equals(statusLineAndHeaders, that.statusLineAndHeaders) && Arrays.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(statusLineAndHeaders);
        result = 31 * result + Arrays.hashCode(body);
        return result;
    }

    @Override
    public String toString() {
        return "PreparedResponse{" +
                "statusLineAndHeaders='" + statusLineAndHeaders + '\'' +
                ", body=" + Arrays.toString(body) +
                '}';
    }
}