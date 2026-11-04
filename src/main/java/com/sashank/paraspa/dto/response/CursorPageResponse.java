package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.util.CursorUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Reusable envelope for cursor-based (keyset) pagination.
 * {@code nextCursor} is null on the last page; pass it back as {@code ?cursor=} to get the next page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CursorPageResponse<T> {
    private List<T> items;
    private String nextCursor;

    /**
     * Builds a page from a result fetched with {@code size + 1} rows: the extra row only signals
     * that more data exists and is dropped from the items.
     */
    public static <T> CursorPageResponse<T> of(List<T> fetched, int size, ToLongFunction<T> keyOf) {
        boolean hasMore = fetched.size() > size;
        List<T> items = hasMore ? List.copyOf(fetched.subList(0, size)) : List.copyOf(fetched);
        String nextCursor = hasMore ? CursorUtil.encode(keyOf.applyAsLong(items.get(items.size() - 1))) : null;
        return new CursorPageResponse<>(items, nextCursor);
    }
}
