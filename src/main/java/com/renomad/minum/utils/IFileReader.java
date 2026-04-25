package com.renomad.minum.utils;

import java.io.IOException;

public interface IFileReader {

    /**
     * Reads a file from disk.
     * @throws com.renomad.minum.security.ForbiddenUseException if the requested path includes bad file patterns,
     * mainly ones to escape from the intended directories (like ".." or "/", etc)
     * @throws RuntimeException as a wrapper around any IOException thrown
     */
    byte[] readFile(String path);
}
