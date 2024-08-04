package com.renomad.minum.web;

import java.io.InputStream;

public class FakeBodyProcessor implements IBodyProcessor {
    public Body data;

    @Override
    public Body extractData(InputStream is, Headers h) {
        return data;
    }

    @Override
    public Iterable<UrlEncodedKeyValue> getUrlEncodedDataIterable(InputStream inputStream, long contentLength) {
        return null;
    }

    @Override
    public Iterable<StreamingMultipartPartition> getMultiPartIterable(InputStream inputStream, String boundaryValue, int contentLength) {
        return null;
    }
}
