package com.renomad.minum.utils;

import java.io.IOException;

public interface IFileReader {

    /**
     * Reads a file from disk.
     * <p>
     *     Protects against some common negative cases:
     * </p>
     * <ul>
     *     <li>If path is bad, log and return an empty byte array</li>
     *     <li>If file does not exist, log and return an empty byte array</li>
     * </ul>
     */
    byte[] readFile(String path) throws IOException;
}
