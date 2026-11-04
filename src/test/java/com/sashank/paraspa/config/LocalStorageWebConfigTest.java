package com.sashank.paraspa.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageWebConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void toResourceLocation_shouldEndWithSlash_whenDirectoryDoesNotExistYet() {
        // regression: on a fresh install uploads/ does not exist at startup, and Path.toUri()
        // then omits the trailing slash, which made every /files/** request fail
        Path notCreatedYet = tempDir.resolve("uploads");
        assertThat(notCreatedYet).doesNotExist();

        assertThat(LocalStorageWebConfig.toResourceLocation(notCreatedYet))
                .startsWith("file:")
                .endsWith("/uploads/");
    }

    @Test
    void toResourceLocation_shouldEndWithSingleSlash_whenDirectoryExists() {
        assertThat(LocalStorageWebConfig.toResourceLocation(tempDir)).endsWith("/").doesNotEndWith("//");
    }
}
