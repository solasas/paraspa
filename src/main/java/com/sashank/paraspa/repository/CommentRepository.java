package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** First page of a post's top-level comments, oldest first (creation order = id order), minus blocked authors. */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL AND " + QueryFragments.COMMENT_AUTHOR_NOT_BLOCKED
            + " ORDER BY c.id ASC")
    List<Comment> findTopLevelVisible(@Param("postId") Long postId, @Param("viewerId") Long viewerId, Pageable pageable);

    /** Next page: top-level comments created after the cursor comment, minus blocked authors. */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL AND c.id > :afterId AND "
            + QueryFragments.COMMENT_AUTHOR_NOT_BLOCKED + " ORDER BY c.id ASC")
    List<Comment> findTopLevelVisibleAfter(@Param("postId") Long postId, @Param("afterId") Long afterId,
                                           @Param("viewerId") Long viewerId, Pageable pageable);

    /** Every reply to the given top-level comments, oldest first, minus blocked authors (one query for a page). */
    @EntityGraph(attributePaths = "author")
    @Query("SELECT c FROM Comment c WHERE c.parent.id IN :parentIds AND " + QueryFragments.COMMENT_AUTHOR_NOT_BLOCKED
            + " ORDER BY c.id ASC")
    List<Comment> findRepliesVisible(@Param("parentIds") Collection<Long> parentIds, @Param("viewerId") Long viewerId);

    Optional<Comment> findByIdAndPostId(Long id, Long postId);

    /**
     * Loads the comment and locks its row until the transaction ends, with a real {@code FOR UPDATE}.
     * A new reply takes a {@code FOR KEY SHARE} lock on its parent (foreign key check), and only {@code FOR UPDATE}
     * conflicts with that, so replies wait for the deleting transaction instead of slipping in unseen. That keeps
     * "replies counted" equal to "replies removed by the cascade", and so the post's commentCount exact.
     * <p>
     * Native on purpose: Hibernate turns {@code LockModeType.PESSIMISTIC_WRITE} into {@code FOR NO KEY UPDATE} on
     * PostgreSQL, which does NOT conflict with {@code FOR KEY SHARE} and would leave this race open.
     */
    @Query(nativeQuery = true, value = "SELECT * FROM comments WHERE id = :id FOR UPDATE")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);

    long countByParentId(Long parentId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
