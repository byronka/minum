package com.renomad.minum.utils;

import java.io.ByteArrayOutputStream;

class MyByteArrayOutputStream extends MyAbstractByteArrayOutputStream {

    private final ByteArrayOutputStream baos;

    public MyByteArrayOutputStream(ByteArrayOutputStream baos) {
        this.baos = baos;
    }

    @Override
    byte[] toByteArray() {
        return this.baos.toByteArray();
    }

    @Override
    public void write(int b) {
        this.baos.write(b);
    }
}
