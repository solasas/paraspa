package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Follow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /** Followers of a user, newest first. */
    List<Follow> findByFollowingIdOrderByCreatedAtDescIdDesc(Long followingId, Pageable pageable);

    /** Users a user follows, newest first. */
    List<Follow> findByFollowerIdOrderByCreatedAtDescIdDesc(Long followerId, Pageable pageable);

    long countByFollowingId(Long followingId);

    long countByFollowerId(Long followerId);

    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);
}
