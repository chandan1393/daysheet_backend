package com.daysheet.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/** Where uploaded files are kept. LocalFileStorage writes to disk; add an S3 version later without touching callers. */
public interface FileStorage {

    /** Stores the bytes and returns a key to find them again. */
    String save(Long workspaceId, InputStream content) throws IOException;

    Resource load(String key);

    void delete(String key);
}
