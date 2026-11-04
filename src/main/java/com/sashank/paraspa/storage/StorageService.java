package com.sashank.paraspa.storage;

/**
 * Abstraction over where uploaded files live. The local-disk implementation is the default;
 * an S3 (or other) implementation can replace it by providing another bean and setting
 * {@code storage.type}.
 */
public interface StorageService {

    /**
     * Stores the content and returns the public URL (or root-relative path) clients use to fetch it.
     *
     * @param directory logical folder, e.g. "avatars"
     * @param filename  file name including extension; letters, digits, '.', '_' and '-' only
     */
    String store(String directory, String filename, byte[] content);

    /** Deletes a file previously returned by {@link #store}. Unknown or foreign URLs are ignored. */
    void delete(String url);
}
