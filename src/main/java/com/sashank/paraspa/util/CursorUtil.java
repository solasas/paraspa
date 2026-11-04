package com.sashank.paraspa.util;

import com.sashank.paraspa.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Opaque cursor encoding for keyset pagination. A cursor wraps the id of the last
 * item on the previous page; clients must treat it as an opaque string.
 */
public final class CursorUtil {

    private CursorUtil() {
    }

    /** Position of an item in a (createdAt, id) ordering. */
    public record Keyset(LocalDateTime createdAt, long id) {
    }

    /**
     * Position in a ranked list: the score and id of the last item, plus the instant ({@code asOf}, epoch seconds)
     * the ranking was computed at. Pinning {@code asOf} keeps the time decay identical on every page.
     */
    public record ScoreKeyset(long asOfEpochSeconds, double score, long id) {
    }

    public static String encodeScore(long asOfEpochSeconds, double score, long id) {
        String raw = asOfEpochSeconds + "|" + score + "|" + id;   // Double.toString round-trips exactly
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for a missing/blank cursor (first page); throws 400 for a malformed one. */
    public static ScoreKeyset decodeScore(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException();
            }
            long asOf = Long.parseLong(parts[0]);
            double score = Double.parseDouble(parts[1]);
            long id = Long.parseLong(parts[2]);
            if (asOf < 0 || id < 0 || Double.isNaN(score) || Double.isInfinite(score) || score < 0) {
                throw new IllegalArgumentException();
            }
            return new ScoreKeyset(asOf, score, id);
        } catch (IllegalArgumentException e) {   // includes NumberFormatException
            throw new BadRequestException("Invalid cursor");
        }
    }

    /** Cursor for lists ordered by (createdAt, id): both values, so items sharing a timestamp stay distinct. */
    public static String encode(LocalDateTime createdAt, long id) {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for a missing/blank cursor (first page); throws 400 for a malformed one. */
    public static Keyset decodeKeyset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int bar = raw.lastIndexOf('|');
            if (bar < 0) {
                throw new IllegalArgumentException();
            }
            long id = Long.parseLong(raw.substring(bar + 1));
            if (id < 0) {
                throw new IllegalArgumentException();
            }
            return new Keyset(LocalDateTime.parse(raw.substring(0, bar)), id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BadRequestException("Invalid cursor");
        }
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
