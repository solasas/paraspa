package com.sashank.paraspa.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDiskStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalDiskStorageService service() {
        return new LocalDiskStorageService(tempDir.toString());
    }

    @Test
    void store_shouldWriteFileAndReturnPublicPath() throws IOException {
        String url = service().store("avatars", "1-abc.png", new byte[]{1, 2, 3});

        assertThat(url).isEqualTo("/files/avatars/1-abc.png");
        assertThat(Files.readAllBytes(tempDir.resolve("avatars/1-abc.png"))).containsExactly(1, 2, 3);
    }

    @Test
    void store_shouldRejectPathTraversalInFilename() {
        assertThatThrownBy(() -> service().store("avatars", "../evil.png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().store("avatars", "a/b.png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_shouldRejectPathTraversalInDirectory() {
        assertThatThrownBy(() -> service().store("..", "x.png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().store("a/../..", "x.png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_shouldNeverWriteOutsideRoot() {
        try {
            service().store("avatars", "..%2Fevil.png", new byte[]{1});
        } catch (IllegalArgumentException ignored) {
            // '%' is not in the allowed character set
        }
        assertThat(tempDir.getParent().resolve("evil.png")).doesNotExist();
    }

    @Test
    void delete_shouldRemoveStoredFile() {
        LocalDiskStorageService service = service();
        String url = service.store("avatars", "1-abc.png", new byte[]{1});

        service.delete(url);

        assertThat(tempDir.resolve("avatars/1-abc.png")).doesNotExist();
    }

    @Test
    void delete_shouldIgnoreMissingFileAndNull() {
        LocalDiskStorageService service = service();

        service.delete("/files/avatars/missing.png");
        service.delete(null);
    }

    @Test
    void delete_shouldIgnoreForeignUrlsAndTraversalAttempts() throws IOException {
        Path outside = Files.createFile(tempDir.resolveSibling(tempDir.getFileName() + "-outside.txt"));
        try {
            LocalDiskStorageService service = service();

            service.delete("https://cdn.example.com/avatars/1.png");
            service.delete("/files/../" + outside.getFileName());
            service.delete("/files/avatars/../../" + outside.getFileName());

            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
