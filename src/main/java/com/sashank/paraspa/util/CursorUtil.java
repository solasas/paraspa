package com.sashank.paraspa.util;

import com.sashank.paraspa.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque cursor encoding for keyset pagination. A cursor wraps the id of the last
 * item on the previous page; clients must treat it as an opaque string.
 */
public final class CursorUtil {

    private CursorUtil() {
    }

    public static String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(lastId).getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for a missing/blank cursor (meaning "first page"); throws 400 for a malformed one. */
    public static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            long id = Long.parseLong(raw);
            if (id < 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid cursor");
        }
    }
}
