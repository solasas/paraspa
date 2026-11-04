package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import java.util.Optional;

public interface NotificationService {

    /** A notification that was really stored, with the address to push it to. */
    record Created(NotificationResponse notification, String recipientEmail) {
    }

    /**
     * Stores a notification in its own transaction. Returns empty (and stores nothing) when
     * <ul>
     *   <li>the actor is the recipient: nobody is notified about their own actions;</li>
     *   <li>it is a LIKE or FOLLOW the recipient already has from this actor for this target (collapsed).</li>
     * </ul>
     * A MESSAGE from someone who already has an unread MESSAGE notification for the recipient does not add a second
     * one: that notification is pointed at the newest message and moved to the top again (and returned).
     * A simultaneous duplicate LIKE/FOLLOW makes the unique constraint throw
     * {@link org.springframework.dao.DataIntegrityViolationException} (the transaction is rolled back cleanly).
     */
    Optional<Created> create(Long recipientId, Long actorId, NotificationType type,
                             NotificationTargetType targetType, Long targetId);

    /** The user's notifications, newest first, cursor-paginated on (createdAt, id). */
    CursorPageResponse<NotificationResponse> getNotifications(Long userId, String cursor, int size);

    long getUnreadCount(Long userId);

    /** Marks one of the user's notifications read. Idempotent. Somebody else's or missing: 404. */
    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);
}
