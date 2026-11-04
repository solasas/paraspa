package com.sashank.paraspa.util;

import com.sashank.paraspa.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationUtilTest {

    @Test
    void defaultsAreTwentyAndMaxIsOneHundred() {
        assertThat(PaginationUtil.DEFAULT_PAGE_SIZE).isEqualTo(20);
        assertThat(PaginationUtil.MAX_PAGE_SIZE).isEqualTo(100);
    }

    @Test
    void toPageable_shouldAcceptBoundaryValues() {
        Pageable pageable = PaginationUtil.toPageable(3, 100);

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(PaginationUtil.toPageable(0, 1).getPageSize()).isEqualTo(1);
    }

    @Test
    void toPageable_shouldRejectNegativePage() {
        assertThatThrownBy(() -> PaginationUtil.toPageable(-1, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void toPageable_shouldRejectSizeZero() {
        assertThatThrownBy(() -> PaginationUtil.toPageable(0, 0))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void toPageable_shouldRejectSizeAboveMax() {
        assertThatThrownBy(() -> PaginationUtil.toPageable(0, 101))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100");
    }

    @Test
    void slice_shouldReturnRequestedWindow() {
        List<Integer> all = IntStream.rangeClosed(1, 25).boxed().toList();

        assertThat(PaginationUtil.slice(all, 0, 10)).containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
        assertThat(PaginationUtil.slice(all, 2, 10)).containsExactly(21, 22, 23, 24, 25);
    }

    @Test
    void slice_shouldReturnEmptyListPastTheEnd() {
        assertThat(PaginationUtil.slice(List.of(1, 2, 3), 5, 10)).isEmpty();
    }

    @Test
    void slice_shouldRejectInvalidParameters() {
        assertThatThrownBy(() -> PaginationUtil.slice(List.of(1), 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void requireValidSize_shouldRejectOutOfRange() {
        assertThat(PaginationUtil.requireValidSize(50)).isEqualTo(50);
        assertThatThrownBy(() -> PaginationUtil.requireValidSize(0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PaginationUtil.requireValidSize(101)).isInstanceOf(BadRequestException.class);
    }
}
