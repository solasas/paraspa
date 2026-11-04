package com.sashank.paraspa.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Selects the ids of a page of the home feed: posts by the people I follow plus my own, newest first.
 * <p>
 * Plain SQL, not HQL, for two reasons that were measured on PostgreSQL (300k posts, user following 300 people):
 * <ul>
 *   <li><b>Row-value cursor.</b> {@code (created_at, id) < (:createdAt, :id)} is a range on the
 *       {@code (created_at DESC, id DESC)} index (V7): 0.6-3.5 ms on every page. The equivalent
 *       {@code created_at < t OR (created_at = t AND id < i)} cannot use the index as a range and took up to
 *       193 ms. HQL (Hibernate 6.3) cannot express a {@code <} comparison between tuples.</li>
 *   <li><b>{@code IN (follows UNION ALL me)}</b> instead of {@code author = me OR author IN (follows)}: the OR form
 *       is a sequential scan of the posts table (69 ms vs 15 ms without the new index, 0.7 ms with it).</li>
 * </ul>
 * The ids come back in feed order; the caller loads the posts (authors via JOIN FETCH, media and skill tags in
 * batches), so a page costs a fixed number of statements however many posts it holds.
 */
@Repository
public class FeedQuery {

    /** One row of the feed: enough to load the post and to build the next cursor. */
    public record Row(long postId, LocalDateTime createdAt) {
    }

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    /**
     * @param afterCreatedAt with {@code afterId}: only posts strictly older than this position; null = first page
     * @param limit          rows to return (callers ask for page size + 1)
     */
    public List<Row> findFeed(Long me, LocalDateTime afterCreatedAt, Long afterId, int limit) {
        StringBuilder sql = new StringBuilder()
                .append("SELECT p.id AS id, p.created_at AS created_at FROM posts p")
                .append(" WHERE p.author_id IN (SELECT f.following_id FROM follows f WHERE f.follower_id = :me")
                .append("                        UNION ALL SELECT :me)");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("me", me).addValue("limit", limit);
        if (afterCreatedAt != null && afterId != null) {
            sql.append(" AND (p.created_at, p.id) < (:afterCreatedAt, :afterId)");
            params.addValue("afterCreatedAt", afterCreatedAt).addValue("afterId", afterId);
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :limit");

        return jdbc.query(sql.toString(), params,
                (rs, rowNum) -> new Row(rs.getLong("id"), rs.getTimestamp("created_at").toLocalDateTime()));
    }
}
