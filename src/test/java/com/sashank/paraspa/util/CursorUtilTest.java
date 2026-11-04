package com.sashank.paraspa.util;

import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorUtilTest {

    @Test
    void encodeThenDecode_shouldRoundTrip() {
        assertThat(CursorUtil.decode(CursorUtil.encode(12345L))).isEqualTo(12345L);
    }

    @Test
    void encode_shouldProduceUrlSafeOpaqueString() {
        assertThat(CursorUtil.encode(42L)).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void decode_shouldReturnNullForMissingCursor() {
        assertThat(CursorUtil.decode(null)).isNull();
        assertThat(CursorUtil.decode("  ")).isNull();
    }

    @Test
    void decode_shouldRejectGarbage() {
        assertThatThrownBy(() -> CursorUtil.decode("not a cursor!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid cursor");
    }

    @Test
    void decode_shouldRejectValidBase64ThatIsNotANumber() {
        String notANumber = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("abc".getBytes());

        assertThatThrownBy(() -> CursorUtil.decode(notANumber))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void decode_shouldRejectNegativeIds() {
        String negative = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("-5".getBytes());

        assertThatThrownBy(() -> CursorUtil.decode(negative))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cursorPage_shouldDropExtraRowAndPointCursorAtLastKeptItem() {
        // fetched size + 1 rows => there is a next page
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(30L, 20L, 10L), 2, Long::longValue);

        assertThat(page.getItems()).containsExactly(30L, 20L);
        assertThat(CursorUtil.decode(page.getNextCursor())).isEqualTo(20L);
    }

    @Test
    void cursorPage_shouldHaveNoCursorOnLastPage() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(30L, 20L), 2, Long::longValue);

        assertThat(page.getItems()).containsExactly(30L, 20L);
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void cursorPage_shouldHandleEmptyResult() {
        CursorPageResponse<Long> page = CursorPageResponse.of(List.of(), 20, Long::longValue);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    // ---- composite (createdAt, id) cursor

    @Test
    void keysetCursor_shouldRoundTripIncludingMicroseconds() {
        java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 11, 4, 15, 56, 52, 709_658_000);

        CursorUtil.Keyset keyset = CursorUtil.decodeKeyset(CursorUtil.encode(at, 123L));

        assertThat(keyset.createdAt()).isEqualTo(at);
        assertThat(keyset.id()).isEqualTo(123L);
    }

    @Test
    void keysetCursor_shouldKeepPostsThatShareATimestampDistinct() {
        java.time.LocalDateTime at = java.time.LocalDateTime.of(2026, 11, 4, 12, 0);

        assertThat(CursorUtil.encode(at, 1L)).isNotEqualTo(CursorUtil.encode(at, 2L));
    }

    @Test
    void keysetCursor_shouldBeNullForMissingCursor() {
        assertThat(CursorUtil.decodeKeyset(null)).isNull();
        assertThat(CursorUtil.decodeKeyset("")).isNull();
    }

    @Test
    void keysetCursor_shouldRejectMalformedValues() {
        java.util.function.Function<String, String> enc = raw ->
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());

        for (String bad : List.of("%%%", enc.apply("garbage"), enc.apply("2026-11-04T12:00|abc"),
                enc.apply("not-a-date|5"), enc.apply("2026-11-04T12:00|-3"), CursorUtil.encode(42L))) {
            assertThatThrownBy(() -> CursorUtil.decodeKeyset(bad))
                    .as("cursor %s", bad)
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Invalid cursor");
        }
    }

    @Test
    void cursorPage_ofCursor_shouldUseTheSuppliedCursorFunction() {
        CursorPageResponse<Long> page = CursorPageResponse.ofCursor(List.of(3L, 2L, 1L), 2, id -> "after-" + id);

        assertThat(page.getItems()).containsExactly(3L, 2L);
        assertThat(page.getNextCursor()).isEqualTo("after-2");
        assertThat(CursorPageResponse.ofCursor(List.of(3L, 2L), 2, id -> "x").getNextCursor()).isNull();
    }
}
