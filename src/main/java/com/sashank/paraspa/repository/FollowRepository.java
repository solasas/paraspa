package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.enums.FollowStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    // ---- any follow row, accepted or still waiting for approval

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /** True when the follower has an ACCEPTED follow (a pending request does not count). */
    boolean existsByFollowerIdAndFollowingIdAndStatus(Long followerId, Long followingId, FollowStatus status);

    long countByFollowingIdAndStatus(Long followingId, FollowStatus status);

    long countByFollowerIdAndStatus(Long followerId, FollowStatus status);

    // ---- lists: accepted follows only, and never the people hidden from the viewer by a block

    /** Accepted followers of {@code userId}, newest first, as seen by {@code viewerId}. */
    @EntityGraph(attributePaths = "follower")
    @Query("SELECT f FROM Follow f WHERE f.following.id = :userId AND f.status = " + QueryFragments.ACCEPTED
            + " AND NOT EXISTS (SELECT 1 FROM Block b WHERE (b.blocker.id = :viewerId AND b.blocked.id = f.follower.id)"
            + " OR (b.blocked.id = :viewerId AND b.blocker.id = f.follower.id))"
            + " ORDER BY f.createdAt DESC, f.id DESC")
    List<Follow> findAcceptedFollowers(@Param("userId") Long userId, @Param("viewerId") Long viewerId, Pageable pageable);

    /** People {@code userId} follows (accepted), newest first, as seen by {@code viewerId}. */
    @EntityGraph(attributePaths = "following")
    @Query("SELECT f FROM Follow f WHERE f.follower.id = :userId AND f.status = " + QueryFragments.ACCEPTED
            + " AND NOT EXISTS (SELECT 1 FROM Block b WHERE (b.blocker.id = :viewerId AND b.blocked.id = f.following.id)"
            + " OR (b.blocked.id = :viewerId AND b.blocker.id = f.following.id))"
            + " ORDER BY f.createdAt DESC, f.id DESC")
    List<Follow> findAcceptedFollowing(@Param("userId") Long userId, @Param("viewerId") Long viewerId, Pageable pageable);

    /** Ids of the people the user follows with an accepted follow: whose posts land in their feed. */
    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId AND f.status = " + QueryFragments.ACCEPTED)
    List<Long> findAcceptedFollowingIds(@Param("followerId") Long followerId);

    /** Ids of everyone the user follows OR has asked to follow (used to stop suggesting them). */
    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId")
    List<Long> findFollowedOrRequestedIds(@Param("followerId") Long followerId);

    // ---- follow requests for a private account

    /** Requests waiting for the owner, newest first, with the requester loaded. */
    @EntityGraph(attributePaths = "follower")
    List<Follow> findByFollowingIdAndStatusOrderByCreatedAtDescIdDesc(Long followingId, FollowStatus status, Pageable pageable);

    Optional<Follow> findByIdAndFollowingIdAndStatus(Long id, Long followingId, FollowStatus status);

    /** Approves every waiting request of an account (used when it stops being private). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Follow f SET f.status = " + QueryFragments.ACCEPTED + " WHERE f.following.id = :ownerId AND f.status = " + QueryFragments.PENDING)
    int acceptAllPending(@Param("ownerId") Long ownerId);

    /** Removes the follow rows between two users in both directions, whatever their status (used when blocking). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Follow f WHERE (f.follower.id = :a AND f.following.id = :b) OR (f.follower.id = :b AND f.following.id = :a)")
    int deleteBetween(@Param("a") Long a, @Param("b") Long b);
}
