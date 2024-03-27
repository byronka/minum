package com.renomad.minum.web;


import java.util.Arrays;
import java.util.Objects;

/**
 * This type is used to contain the results of modifications
 * applied to the outgoing data just before sending
 * @param statusLineAndHeaders string values separated by CRLF, per HTTP spec
 * @param body the body of the message, as bytes.  May be compressed.
 */
record PreparedResponse(String statusLineAndHeaders, byte[] body){
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PreparedResponse that = (PreparedResponse) o;
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