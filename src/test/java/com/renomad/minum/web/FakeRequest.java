package com.renomad.minum.web;

public class FakeRequest implements IRequest {
    public RequestLine requestLine;

    @Override
    public Headers getHeaders() {
        return null;
    }

    @Override
    public RequestLine getRequestLine() {
        return requestLine;
    }

    @Override
    public Body getBody() {
        return null;
    }

    @Override
    public String getRemoteRequester() {
        return null;
    }

    @Override
    public ISocketWrapper getSocketWrapper() {
        return null;
    }

    @Override
    public Iterable<UrlEncodedKeyValue> getUrlEncodedIterable() {
        return null;
    }

    @Override
    public Iterable<StreamingMultipartPartition> getMultipartIterable() {
        return null;
    }
}
