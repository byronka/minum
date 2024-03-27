package com.renomad.minum.utils;

import java.io.IOException;

public interface IFileReader {
    byte[] readFile(String path) throws IOException;
}
