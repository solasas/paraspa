package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Block;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** True when either of the two has blocked the other. */
    @Query("SELECT COUNT(b) > 0 FROM Block b WHERE (b.blocker.id = :a AND b.blocked.id = :b) "
            + "OR (b.blocker.id = :b AND b.blocked.id = :a)")
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    /** Everyone who is hidden from {@code userId} and from whom {@code userId} is hidden: blocked either way. */
    @Query("SELECT CASE WHEN b.blocker.id = :userId THEN b.blocked.id ELSE b.blocker.id END FROM Block b "
            + "WHERE b.blocker.id = :userId OR b.blocked.id = :userId")
    List<Long> findHiddenUserIds(@Param("userId") Long userId);

    /** The people this user blocked (not the ones who blocked them), newest block first, with the user loaded. */
    @EntityGraph(attributePaths = "blocked")
    List<Block> findByBlockerIdOrderByIdDesc(Long blockerId, Pageable pageable);

    /**
     * Blocks the user unless already blocked. 1 if a row was inserted, 0 if it existed. A single
     * INSERT ... ON CONFLICT DO NOTHING so two simultaneous taps cannot both insert (no exception that would also
     * abort the transaction that goes on to remove the follows).
     */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO blocks (blocker_id, blocked_id, created_at) "
            + "VALUES (:blockerId, :blockedId, :createdAt) ON CONFLICT (blocker_id, blocked_id) DO NOTHING")
    int insertIfAbsent(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId,
                       @Param("createdAt") LocalDateTime createdAt);

    /** Removes the block. 1 if there was one. */
    @Modifying
    @Query("DELETE FROM Block b WHERE b.blocker.id = :blockerId AND b.blocked.id = :blockedId")
    int deleteBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
