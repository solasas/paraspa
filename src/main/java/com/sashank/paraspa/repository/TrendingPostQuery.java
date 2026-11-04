package com.sashank.paraspa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Ranks recent posts for GET /api/explore. Plain SQL because the score is computed in the database:
 * <pre>
 *   score = (likeCount + 2 * commentCount) / (ageInHours + 2) ^ 1.5
 * </pre>
 * Engagement is divided by a power of the age, so a fresh post with a few likes can outrank a week-old post with
 * many (the +2 stops brand-new posts from dividing by ~0). Ranking is relative to {@code asOf}, not "now", so
 * every page of one scroll uses the same instant.
 * <p>
 * The arithmetic is done in DOUBLE PRECISION on purpose: with PostgreSQL's default numeric literals the same query
 * took 689 ms instead of 72 ms (300k posts, ~48k in the window).
 * Cost is proportional to the number of posts in the window, not to the page size; see the README.
 */
@Repository
public class TrendingPostQuery {

    public static final int WINDOW_DAYS = 7;

    /** One ranked row. */
    public record Scored(long postId, double score) {
    }

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * @param viewerId   excluded from the results together with everyone they follow
     * @param skillId    optional skill tag filter (null = any)
     * @param asOf       the ranking instant; the window is (asOf - 7 days, asOf]
     * @param afterScore with {@code afterId}: continue strictly after this (score, id) position; null = first page
     * @param limit      rows to return (callers ask for page size + 1)
     */
    public List<Scored> findTrending(Long viewerId, Long skillId, LocalDateTime asOf,
                                     Double afterScore, Long afterId, int limit) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT id, score FROM (")
                .append(" SELECT p.id AS id,")
                // every operand is an explicit DOUBLE PRECISION: untyped literals are NUMERIC on PostgreSQL (10x slower,
                // 689 ms vs 72 ms) and arbitrary-precision DECFLOAT on H2 (the query never finishes)
                .append("  CAST(p.like_count + 2 * p.comment_count AS DOUBLE PRECISION) /")
                .append("  POWER(((CAST(:asOfEpoch AS DOUBLE PRECISION) - CAST(EXTRACT(EPOCH FROM p.created_at) AS DOUBLE PRECISION))")
                .append("         / CAST(3600 AS DOUBLE PRECISION)) + CAST(2 AS DOUBLE PRECISION), CAST(1.5 AS DOUBLE PRECISION)) AS score")
                .append(" FROM posts p")
                .append(" WHERE p.created_at > :since AND p.created_at <= :asOf")
                .append("  AND p.author_id <> :viewer")
                .append("  AND NOT EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = :viewer AND f.following_id = p.author_id)");
        if (skillId != null) {
            sql.append("  AND EXISTS (SELECT 1 FROM post_skills ps WHERE ps.post_id = p.id AND ps.skill_id = :skillId)");
        }
        sql.append(") ranked");
        if (afterScore != null && afterId != null) {
            sql.append(" WHERE (score < :afterScore OR (score = :afterScore AND id < :afterId))");
        }
        sql.append(" ORDER BY score DESC, id DESC LIMIT :limit");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("asOfEpoch", asOf.toEpochSecond(ZoneOffset.UTC))
                .addValue("asOf", asOf)
                .addValue("since", asOf.minusDays(WINDOW_DAYS))
                .addValue("viewer", viewerId)
                .addValue("limit", limit);
        if (skillId != null) {
            params.addValue("skillId", skillId);
        }
        if (afterScore != null && afterId != null) {
            params.addValue("afterScore", afterScore).addValue("afterId", afterId);
        }

        return jdbc.query(sql.toString(), params,
                (rs, rowNum) -> new Scored(rs.getLong("id"), rs.getDouble("score")));
    }
}
