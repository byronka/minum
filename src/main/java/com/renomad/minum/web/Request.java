package com.renomad.minum.web;

import java.util.Objects;

/**
 * An HTTP request.
 * <p>
 * From <a href="https://en.wikipedia.org/wiki/HTTP#Request_syntax">Wikipedia</a>
 * </p>
 *<p>
 *     A client sends request messages to the server, which consist of:
 *</p>
 * <ul>
 *     <li>
 *      a request line, consisting of the case-sensitive request
 *      method, a space, the requested URL, another space, the
 *      protocol version, a carriage return, and a line feed, e.g.:
 *      <pre>
 *          GET /images/logo.png HTTP/1.1
 *      </pre>
 *      </li>
 *
 *      <li>
 *      zero or more request header fields (at least 1 or more
 *      headers in case of HTTP/1.1), each consisting of the case-insensitive
 *      field name, a colon, optional leading whitespace, the field
 *      value, an optional trailing whitespace and ending with a
 *      carriage return and a line feed, e.g.:
 *
 *      <pre>
 *      Host: www.example.com
 *      Accept-Language: en
 *      </pre>
 *      </li>
 *
 *      <li>
 *      an empty line, consisting of a carriage return and a line feed;
 *      </li>
 *
 *      <li>
 *      an optional message body.
 *      </li>
 *      In the HTTP/1.1 protocol, all header fields except Host: hostname are optional.
 * </ul>
 *
 * <p>
 * A request line containing only the path name is accepted by servers to
 * maintain compatibility with HTTP clients before the HTTP/1.0 specification in RFC 1945.
 *</p>
 *
 */
public final class Request implements IRequest {

    private final Headers headers;
    private final RequestLine requestLine;
    private Body body;
    private final String remoteRequester;
    private final ISocketWrapper socketWrapper;
    private final IBodyProcessor bodyProcessor;
    private boolean hasStartedReadingBody;

    /**
     * Constructor for a HTTP request
     * @param  remoteRequester This is the remote address making the request
     */
    public Request(Headers headers,
            RequestLine requestLine,
            String remoteRequester,
            ISocketWrapper socketWrapper,
            IBodyProcessor bodyProcessor
    ) {
        this.headers = headers;
        this.requestLine = requestLine;
        this.remoteRequester = remoteRequester;
        this.socketWrapper = socketWrapper;
        this.bodyProcessor = bodyProcessor;
        this.hasStartedReadingBody = false;
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public RequestLine getRequestLine() {
        return requestLine;
    }

    @Override
    public Body getBody() {
        if (hasStartedReadingBody) {
            throw new WebServerException("The InputStream in Request has already been accessed for reading, preventing body extraction from stream." +
                    " If intending to use getBody(), use it exclusively");
        }
        if (body == null) {
            body = bodyProcessor.extractData(socketWrapper.getInputStream(), headers);
        }
        return body;
    }

    @Override
    public String getRemoteRequester() {
        return remoteRequester;
    }

    @Override
    public ISocketWrapper getSocketWrapper() {
        checkForExistingBody();
        hasStartedReadingBody = true;
        return socketWrapper;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return hasStartedReadingBody == request.hasStartedReadingBody && Objects.equals(headers, request.headers) && Objects.equals(requestLine, request.requestLine) && Objects.equals(body, request.body) && Objects.equals(remoteRequester, request.remoteRequester) && Objects.equals(socketWrapper, request.socketWrapper) && Objects.equals(bodyProcessor, request.bodyProcessor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers, requestLine, body, remoteRequester, socketWrapper, bodyProcessor, hasStartedReadingBody);
    }

    @Override
    public String toString() {
        return "Request{" +
                "headers=" + headers +
                ", requestLine=" + requestLine +
                ", body=" + body +
                ", remoteRequester='" + remoteRequester + '\'' +
                ", socketWrapper=" + socketWrapper +
                ", hasStartedReadingBody=" + hasStartedReadingBody +
                '}';
    }

    @Override
    public Iterable<UrlEncodedKeyValue> getUrlEncodedIterable() {
        checkForExistingBody();
        if (!headers.contentType().contains("application/x-www-form-urlencoded")) {
            throw new WebServerException("This request was not sent with a content type of application/x-www-form-urlencoded.  The content type was: " + headers.contentType());
        }
        return bodyProcessor.getUrlEncodedDataIterable(getSocketWrapper().getInputStream(), getHeaders().contentLength());
    }

    /**
     * This method is for verifying that the body is null, and throwing an exception
     * if not.  Several of the methods in this class depend on the InputStream not
     * having been read already - if the body was read, then the InputStream is finished,
     * and any further reading would be incorrect.
     */
    private void checkForExistingBody() {
        if (body != null) {
            throw new WebServerException("Requesting this after getting the body with getBody() will result in incorrect behavior.  " +
                    "If you intend to work with the Request at this level, do not use getBody");
        }
        if (hasStartedReadingBody) {
            throw new WebServerException("The InputStream has begun processing elsewhere.  Results are invalid.");
        }
    }

    @Override
    public Iterable<StreamingMultipartPartition> getMultipartIterable() {
        checkForExistingBody();
        if (!headers.contentType().contains("multipart/form-data")) {
            throw new WebServerException("This request was not sent with a content type of multipart/form-data.  The content type was: " + headers.contentType());
        }
        String boundaryKey = "boundary=";
        String contentType = getHeaders().contentType();
        int indexOfBoundaryKey = contentType.indexOf(boundaryKey);
        String boundaryValue = "";
        if (indexOfBoundaryKey > 0) {
            // grab all the text after the key to obtain the boundary value
            boundaryValue = contentType.substring(indexOfBoundaryKey + boundaryKey.length());
        } else {
            String parsingError = "Did not find a valid boundary value for the multipart input. Returning an empty map and the raw bytes for the body. Header was: " + contentType;
            throw new WebServerException(parsingError);
        }

        if (boundaryValue.isBlank()) {
            String parsingError = "Boundary value was blank. Returning an empty map and the raw bytes for the body. Header was: " + contentType;
            throw new WebServerException(parsingError);
        }

        return bodyProcessor.getMultiPartIterable(getSocketWrapper().getInputStream(), boundaryValue ,getHeaders().contentLength());
    }
}
