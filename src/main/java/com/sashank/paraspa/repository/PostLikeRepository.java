package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.PostLike;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /**
     * Likes the post unless the user already did. Returns 1 if a row was inserted, 0 if it already existed.
     * Done as a single INSERT ... ON CONFLICT DO NOTHING so two simultaneous requests from the same user
     * cannot both insert (no exception, which would also abort the surrounding transaction), and so the
     * caller can bump the counter only when this call really added the like.
     */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO post_likes (post_id, user_id, created_at) "
            + "VALUES (:postId, :userId, :createdAt) ON CONFLICT (post_id, user_id) DO NOTHING")
    int insertIfAbsent(@Param("postId") Long postId, @Param("userId") Long userId,
                       @Param("createdAt") LocalDateTime createdAt);

    /** Removes the user's like. Returns 1 if there was one, 0 otherwise. */
    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.post.id = :postId AND l.user.id = :userId")
    int deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);

    /** Of the given posts, the ids this user has liked (one query for a whole page). */
    @Query("SELECT l.post.id FROM PostLike l WHERE l.user.id = :userId AND l.post.id IN :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    /** Who liked the post, most recent like first. Fetches the users in the same query. */
    @EntityGraph(attributePaths = "user")
    List<PostLike> findByPostIdOrderByCreatedAtDescIdDesc(Long postId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PostLike l WHERE l.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
