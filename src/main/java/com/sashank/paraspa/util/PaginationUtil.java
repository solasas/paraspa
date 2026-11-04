package com.sashank.paraspa.util;

import com.sashank.paraspa.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Shared page/size rules for every paginated list endpoint:
 * page is zero-based, default size 20, maximum size 100.
 */
public final class PaginationUtil {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtil() {
    }

    public static Pageable toPageable(int page, int size) {
        validate(page, size);
        return PageRequest.of(page, size);
    }

    /** Validates page and size, then returns the requested slice of an in-memory list. */
    public static <T> List<T> slice(List<T> all, int page, int size) {
        validate(page, size);
        long from = (long) page * size;
        if (from >= all.size()) {
            return List.of();
        }
        int end = (int) Math.min(from + size, all.size());
        return all.subList((int) from, end);
    }

    public static int requireValidSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return size;
    }

    private static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be >= 0 and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
