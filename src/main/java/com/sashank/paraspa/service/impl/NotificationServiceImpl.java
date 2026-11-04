package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.entity.Notification;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.NotificationRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.NotificationService;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class NotificationServiceImpl implements NotificationService {

    /** Things that happen once and may be repeated by toggling (like/unlike, follow/unfollow): notify only once. */
    private static final Set<NotificationType> COLLAPSED_FOREVER = EnumSet.of(NotificationType.LIKE, NotificationType.FOLLOW);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    // REQUIRES_NEW: this runs from an AFTER_COMMIT event handler, when the publisher's transaction has already
    // committed; writes there would never be committed without a transaction of their own.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Created> create(Long recipientId, Long actorId, NotificationType type,
                                    NotificationTargetType targetType, Long targetId) {
        if (recipientId == null || actorId == null || recipientId.equals(actorId)) {
            return Optional.empty();
        }

        Optional<User> recipient = userRepository.findById(recipientId);
        Optional<User> actor = userRepository.findById(actorId);
        if (recipient.isEmpty() || actor.isEmpty()) {
            return Optional.empty();   // a user vanished between the action and the notification: nothing to tell
        }

        String dedupeKey = null;
        if (COLLAPSED_FOREVER.contains(type)) {
            dedupeKey = actorId + ":" + type + ":" + targetType + ":" + targetId;
            if (notificationRepository.existsByRecipientIdAndDedupeKey(recipientId, dedupeKey)) {
                return Optional.empty();
            }
        }

        if (type == NotificationType.MESSAGE) {
            Optional<Notification> unread = notificationRepository
                    .findFirstByRecipientIdAndActorIdAndTypeAndReadFalseOrderByIdDesc(recipientId, actorId, type);
            if (unread.isPresent()) {
                Notification existing = unread.get();
                existing.setTargetId(targetId);
                existing.setCreatedAt(LocalDateTime.now());
                return Optional.of(toCreated(notificationRepository.save(existing), recipient.get()));
            }
        }

        Notification saved = notificationRepository.saveAndFlush(Notification.builder()
                .recipient(recipient.get())
                .actor(actor.get())
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .dedupeKey(dedupeKey)
                .build());
        return Optional.of(toCreated(saved, recipient.get()));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<NotificationResponse> getNotifications(Long userId, String cursor, int size) {
        PaginationUtil.requireValidSize(size);
        CursorUtil.Keyset after = CursorUtil.decodeKeyset(cursor);

        Pageable pageable = PageRequest.of(0, size + 1);   // one extra row tells us whether a next page exists
        List<Notification> fetched = after == null
                ? notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(userId, pageable)
                : notificationRepository.findByRecipientIdBefore(userId, after.createdAt(), after.id(), pageable);

        CursorPageResponse<Notification> page = CursorPageResponse.ofCursor(fetched, size,
                n -> CursorUtil.encode(n.getCreatedAt(), n.getId()));

        return new CursorPageResponse<>(
                page.getItems().stream().map(dtoMapper::toNotificationResponse).toList(), page.getNextCursor());
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        if (notificationRepository.markRead(notificationId, userId) == 0) {
            throw new ResourceNotFoundException("Notification not found");
        }
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    private Created toCreated(Notification notification, User recipient) {
        return new Created(dtoMapper.toNotificationResponse(notification), recipient.getEmail());
    }
}
