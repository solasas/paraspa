package com.sashank.paraspa.event;

import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.service.NotificationPushService;
import com.sashank.paraspa.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationPushService pushService;

    @InjectMocks
    private NotificationEventListener listener;

    private static final NotificationResponse PAYLOAD = NotificationResponse.builder().id(900L).build();

    private void willCreate(Long recipient, Long actor, NotificationType type, NotificationTargetType target, Long targetId) {
        when(notificationService.create(recipient, actor, type, target, targetId))
                .thenReturn(Optional.of(new NotificationService.Created(PAYLOAD, "recipient@example.com")));
    }

    // ============================================================ event -> notification

    @Test
    void follow_notifiesTheFollowedPersonAndPointsAtTheFollower() {
        willCreate(2L, 1L, NotificationType.FOLLOW, NotificationTargetType.USER, 1L);

        listener.onUserFollowed(new UserFollowedEvent(1L, 2L));

        verify(pushService).push("recipient@example.com", PAYLOAD);
    }

    @Test
    void like_notifiesThePostAuthorAboutThePost() {
        willCreate(2L, 1L, NotificationType.LIKE, NotificationTargetType.POST, 100L);

        listener.onPostLiked(new PostLikedEvent(100L, 2L, 1L));

        verify(pushService).push("recipient@example.com", PAYLOAD);
    }

    @Test
    void topLevelComment_notifiesThePostAuthorAsACOMMENT() {
        willCreate(2L, 1L, NotificationType.COMMENT, NotificationTargetType.POST, 100L);

        listener.onCommentAdded(new CommentAddedEvent(100L, 2L, 50L, 1L, null));

        verify(pushService).push("recipient@example.com", PAYLOAD);
        verify(notificationService, never()).create(any(), any(), org.mockito.ArgumentMatchers.eq(NotificationType.REPLY), any(), any());
    }

    @Test
    void reply_notifiesTheAuthorOfTheCommentRepliedToAsAREPLY_notThePostAuthor() {
        // post 100 is by user 2; the comment being replied to is by user 3; the reply is by user 1
        willCreate(3L, 1L, NotificationType.REPLY, NotificationTargetType.POST, 100L);

        listener.onCommentAdded(new CommentAddedEvent(100L, 2L, 51L, 1L, 3L));

        verify(pushService).push("recipient@example.com", PAYLOAD);
        verify(notificationService, never()).create(org.mockito.ArgumentMatchers.eq(2L), any(), any(), any(), any());
    }

    @Test
    void exchangeRequest_notifiesTheReceiver() {
        willCreate(2L, 1L, NotificationType.EXCHANGE_REQUEST, NotificationTargetType.EXCHANGE, 77L);

        listener.onExchangeRequested(new ExchangeRequestedEvent(77L, 1L, 2L));

        verify(pushService).push("recipient@example.com", PAYLOAD);
    }

    @Test
    void exchangeAccepted_notifiesTheRequesterWithTheAccepterAsActor() {
        willCreate(1L, 2L, NotificationType.EXCHANGE_ACCEPTED, NotificationTargetType.EXCHANGE, 77L);

        listener.onExchangeAccepted(new ExchangeAcceptedEvent(77L, 1L, 2L));

        verify(pushService).push("recipient@example.com", PAYLOAD);
    }

    @Test
    void message_notifiesTheReceiverAboutTheLatestMessage() {
        willCreate(2L, 1L, NotificationType.MESSAGE, NotificationTargetType.MESSAGE, 900L);

        listener.onMessageSent(new MessageSentEvent(900L, 1L, 2L));

        verify(pushService).push("recipient@example.com", PAYLOAD);
    }

    // ============================================================ nothing created => nothing pushed

    @Test
    void whenNothingIsCreated_noPushHappens() {
        // e.g. a self-notification or a collapsed duplicate: the service returns empty
        when(notificationService.create(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        listener.onPostLiked(new PostLikedEvent(100L, 1L, 1L));
        listener.onUserFollowed(new UserFollowedEvent(1L, 2L));

        verify(pushService, never()).push(any(), any());
    }

    @Test
    void theNotificationIsStoredBeforeItIsPushed() {
        willCreate(2L, 1L, NotificationType.LIKE, NotificationTargetType.POST, 100L);

        listener.onPostLiked(new PostLikedEvent(100L, 2L, 1L));

        InOrder order = inOrder(notificationService, pushService);
        order.verify(notificationService).create(2L, 1L, NotificationType.LIKE, NotificationTargetType.POST, 100L);
        order.verify(pushService).push("recipient@example.com", PAYLOAD);
    }

    // ============================================================ failures never reach the request that caused the event

    @Test
    void aFailureWhileStoringIsSwallowed_andNothingIsPushed() {
        when(notificationService.create(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onPostLiked(new PostLikedEvent(100L, 2L, 1L))).doesNotThrowAnyException();

        verify(pushService, never()).push(any(), any());
    }

    @Test
    void aDuplicateRaceIsSwallowed() {
        when(notificationService.create(any(), any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> listener.onUserFollowed(new UserFollowedEvent(1L, 2L))).doesNotThrowAnyException();

        verify(pushService, never()).push(any(), any());
    }

    // ============================================================ transactional behaviour (guard)

    @Test
    void everyHandlerRunsAfterCommit_alsoWithoutATransaction_andOffTheRequestThread() {
        Method[] handlers = Arrays.stream(NotificationEventListener.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("on")).toArray(Method[]::new);

        assertThat(handlers).hasSize(6);   // one per event type
        for (Method handler : handlers) {
            TransactionalEventListener annotation = handler.getAnnotation(TransactionalEventListener.class);
            assertThat(annotation).as(handler.getName()).isNotNull();
            // AFTER_COMMIT: nothing for a rolled-back action. fallbackExecution: services such as follow and message are
            // not transactional, and without it their events would be dropped silently.
            assertThat(annotation.phase()).as(handler.getName()).isEqualTo(TransactionPhase.AFTER_COMMIT);
            assertThat(annotation.fallbackExecution()).as(handler.getName()).isTrue();
            // off the request thread, or concurrent requests starve the connection pool (see AsyncConfig and
            // NotificationConnectionPoolTest)
            assertThat(handler.getAnnotation(org.springframework.scheduling.annotation.Async.class))
                    .as(handler.getName() + " must be @Async").isNotNull();
            assertThat(handler.getAnnotation(org.springframework.scheduling.annotation.Async.class).value())
                    .isEqualTo("notificationExecutor");
        }
    }
}
