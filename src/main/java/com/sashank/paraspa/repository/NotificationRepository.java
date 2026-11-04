package com.sashank.paraspa.repository;

import com.sashank.paraspa.entity.Notification;
import com.sashank.paraspa.enums.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByRecipientIdAndDedupeKey(Long recipientId, String dedupeKey);

    /** The user's unread notification of this kind from this actor, if any (used to collapse message notifications). */
    Optional<Notification> findFirstByRecipientIdAndActorIdAndTypeAndReadFalseOrderByIdDesc(
            Long recipientId, Long actorId, NotificationType type);

    /** First page of a user's notifications, newest first. The actor comes in the same statement. */
    @EntityGraph(attributePaths = "actor")
    List<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    /** Next page: strictly older than the (createdAt, id) cursor. Served by idx_notifications_recipient_created. */
    @EntityGraph(attributePaths = "actor")
    @Query("SELECT n FROM Notification n WHERE n.recipient.id = :recipientId "
            + "AND (n.createdAt < :createdAt OR (n.createdAt = :createdAt AND n.id < :id)) "
            + "ORDER BY n.createdAt DESC, n.id DESC")
    List<Notification> findByRecipientIdBefore(@Param("recipientId") Long recipientId,
                                               @Param("createdAt") LocalDateTime createdAt,
                                               @Param("id") Long id, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    /**
     * Marks one notification read, only if it belongs to the user. Returns the rows matched: 1 even when it was
     * already read (so the call is idempotent), 0 when it does not exist or is somebody else's.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.recipient.id = :recipientId")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** Marks every unread notification of the user read. Returns how many changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId);
}
