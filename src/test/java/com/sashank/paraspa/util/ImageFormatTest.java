package com.sashank.paraspa.util;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    void detect_shouldRecogniseJpeg() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10))).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.JPEG.getExtension()).isEqualTo("jpg");
    }

    @Test
    void detect_shouldRecognisePng() {
        assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0))).contains(ImageFormat.PNG);
    }

    @Test
    void detect_shouldRecogniseWebp() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ImageFormat.detect(webp)).contains(ImageFormat.WEBP);
    }

    @Test
    void detect_shouldRejectGif() {
        assertThat(ImageFormat.detect("GIF89a....".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }

    @Test
    void detect_shouldRejectRiffThatIsNotWebp() {
        assertThat(ImageFormat.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }

    @Test
    void detect_shouldRejectTextAndSvgAndTooShortInput() {
        assertThat(ImageFormat.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect("hello".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(null)).isEmpty();
    }
}
