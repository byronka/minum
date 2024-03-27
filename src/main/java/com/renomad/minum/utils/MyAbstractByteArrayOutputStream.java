package com.renomad.minum.utils;

import java.io.OutputStream;

abstract class MyAbstractByteArrayOutputStream extends OutputStream {
    abstract byte[] toByteArray();
}
