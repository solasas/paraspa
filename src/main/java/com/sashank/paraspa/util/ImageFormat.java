package com.sashank.paraspa.util;

import java.util.Optional;

/** Allowed upload image types, detected from the file's leading bytes rather than the client-declared type. */
public enum ImageFormat {
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp");

    private final String extension;

    ImageFormat(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return Optional.of(PNG);
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }
}
