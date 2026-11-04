package com.sashank.paraspa.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorageService implements StorageService {

    public static final String URL_PREFIX = "/files/";

    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorageService.class);
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Path root;

    public LocalDiskStorageService(@Value("${storage.local.root:uploads}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public String store(String directory, String filename, byte[] content) {
        requireSafe(directory);
        requireSafe(filename);

        Path target = resolve(directory + "/" + filename);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file", e);
        }
        return URL_PREFIX + directory + "/" + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        String key = url.substring(URL_PREFIX.length());
        String[] parts = key.split("/");
        if (parts.length != 2 || !SAFE_NAME.matcher(parts[0]).matches() || !SAFE_NAME.matcher(parts[1]).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", url, e);
        }
    }

    private void requireSafe(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches() || name.contains("..")) {
            throw new IllegalArgumentException("Unsafe storage path segment");
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes storage root");
        }
        return path;
    }
}
