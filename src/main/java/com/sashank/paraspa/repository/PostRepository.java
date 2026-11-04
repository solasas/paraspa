package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * The general listing (GET /api/posts): newest first, only posts the viewer may see. The unfiltered "all posts"
     * queries were removed so nothing can list a blocked user's or a private account's posts by accident.
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE " + QueryFragments.POST_VISIBLE
            + " ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findVisibleToViewer(@Param("viewerId") Long viewerId, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.author JOIN p.skills s WHERE s.id = :skillId AND " + QueryFragments.POST_VISIBLE
            + " ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findVisibleToViewerBySkill(@Param("viewerId") Long viewerId, @Param("skillId") Long skillId, Pageable pageable);

    List<Post> findByAuthorIdInOrderByCreatedAtDescIdDesc(Collection<Long> authorIds, Pageable pageable);

    List<Post> findByAuthorIdInAndSkills_IdOrderByCreatedAtDescIdDesc(
            Collection<Long> authorIds, Long skillId, Pageable pageable);

    /** First page of an author's posts, newest first. */
    List<Post> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId, Pageable pageable);

    /**
     * Keyset page: the author's posts strictly older than the cursor position, newest first.
     * Ordering on (created_at, id) makes the position unambiguous even when posts share a timestamp.
     */
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId "
            + "AND (p.createdAt < :createdAt OR (p.createdAt = :createdAt AND p.id < :id)) "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    List<Post> findByAuthorIdBefore(@Param("authorId") Long authorId,
                                    @Param("createdAt") LocalDateTime createdAt,
                                    @Param("id") Long id,
                                    Pageable pageable);

    /** Loads posts with their authors in one statement (the caller restores the order). */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id IN :ids")
    List<Post> findAllWithAuthorByIdIn(@Param("ids") Collection<Long> ids);

    // Atomic counter updates (no read-modify-write, so concurrent likes/comments cannot lose updates).
    // clearAutomatically so a following read in the same transaction sees the new value.

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    int incrementCommentCount(@Param("id") Long id);

    /** Lowers the count by {@code n} (a deleted comment plus its replies), never below zero. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = CASE WHEN p.commentCount > :n THEN p.commentCount - :n ELSE 0 END "
            + "WHERE p.id = :id")
    int decrementCommentCount(@Param("id") Long id, @Param("n") long n);
}
