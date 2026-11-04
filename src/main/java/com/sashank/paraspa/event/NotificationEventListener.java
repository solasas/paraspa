package com.sashank.paraspa.event;

import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.service.NotificationPushService;
import com.sashank.paraspa.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sashank.paraspa.config.AsyncConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into notifications, so the services that publish them know nothing about notifications.
 * <p>
 * {@code AFTER_COMMIT}: nothing is created for an action that was rolled back, and a notification can never be
 * seen before the action it describes. {@code fallbackExecution = true}: some services are deliberately not
 * transactional (a follow, a message); without it, an event published outside a transaction would be dropped silently.
 * <p>
 * {@code @Async}: the handlers run on their own small executor ({@link AsyncConfig}), not on the request thread. An
 * AFTER_COMMIT handler on the request thread still holds the request's database connection while storing the
 * notification needs another one; under load that starves the connection pool and every request times out. As a
 * result a notification appears a few milliseconds after the action, not inside the same request.
 * <p>
 * Every handler swallows its errors: a failure here must never break the request that caused the event, which has
 * already succeeded by the time these run.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationPushService pushService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserFollowed(UserFollowedEvent event) {
        // points at the follower: tapping it opens their profile
        deliver(event.followedId(), event.followerId(), NotificationType.FOLLOW, NotificationTargetType.USER, event.followerId());
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostLiked(PostLikedEvent event) {
        deliver(event.postAuthorId(), event.likerId(), NotificationType.LIKE, NotificationTargetType.POST, event.postId());
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommentAdded(CommentAddedEvent event) {
        if (event.parentCommentAuthorId() == null) {
            // a new comment: tell the author of the post
            deliver(event.postAuthorId(), event.authorId(), NotificationType.COMMENT, NotificationTargetType.POST, event.postId());
        } else {
            // a reply: tell the author of the comment that was replied to
            deliver(event.parentCommentAuthorId(), event.authorId(), NotificationType.REPLY, NotificationTargetType.POST, event.postId());
        }
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExchangeRequested(ExchangeRequestedEvent event) {
        deliver(event.receiverId(), event.requesterId(), NotificationType.EXCHANGE_REQUEST,
                NotificationTargetType.EXCHANGE, event.exchangeId());
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExchangeAccepted(ExchangeAcceptedEvent event) {
        deliver(event.requesterId(), event.accepterId(), NotificationType.EXCHANGE_ACCEPTED,
                NotificationTargetType.EXCHANGE, event.exchangeId());
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageSent(MessageSentEvent event) {
        deliver(event.receiverId(), event.senderId(), NotificationType.MESSAGE, NotificationTargetType.MESSAGE, event.messageId());
    }

    private void deliver(Long recipientId, Long actorId, NotificationType type, NotificationTargetType targetType, Long targetId) {
        try {
            // the notification is committed (in its own transaction) before it is pushed
            notificationService.create(recipientId, actorId, type, targetType, targetId)
                    .ifPresent(created -> pushService.push(created.recipientEmail(), created.notification()));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate {} notification for user {} ignored", type, recipientId);   // two identical requests raced
        } catch (Exception e) {
            log.warn("Could not create {} notification for user {}", type, recipientId, e);
        }
    }
}
