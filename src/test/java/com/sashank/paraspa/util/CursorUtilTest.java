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
}
