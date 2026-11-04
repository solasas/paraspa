package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.entity.Notification;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.event.CommentAddedEvent;
import com.sashank.paraspa.event.ExchangeAcceptedEvent;
import com.sashank.paraspa.event.ExchangeRequestedEvent;
import com.sashank.paraspa.event.MessageSentEvent;
import com.sashank.paraspa.event.NotificationEventListener;
import com.sashank.paraspa.event.PostLikedEvent;
import com.sashank.paraspa.event.UserFollowedEvent;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.NotificationRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.NotificationPushService;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The real Spring event machinery and the real queries on an in-memory database: events are handled only after the
 * publisher's transaction commits (never on rollback), still handled when there is no transaction, nobody is notified
 * about their own actions, duplicate likes collapse, and read/unread/paging work on stored rows.
 * <p>
 * Test methods run WITHOUT a surrounding transaction (otherwise nothing would ever commit); data is cleaned up after each.
 * <p>
 * The handlers are called synchronously here (no {@code @EnableAsync} in this slice) so every assertion is deterministic;
 * that they really run on their own executor, and what that buys, is covered by {@link NotificationConnectionPoolTest}
 * and by the end-to-end WebSocket test.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
@Import({NotificationServiceImpl.class, NotificationEventListener.class, NotificationPushService.class, DtoMapper.class})
class NotificationEventIntegrationTest {

    @MockBean private SimpMessagingTemplate messagingTemplate;

    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private NotificationServiceImpl notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private TransactionTemplate tx;
    private User asha;
    private User ben;
    private User cy;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        asha = userRepository.save(User.builder().name("Asha").email("asha@example.com").password("x").build());
        ben = userRepository.save(User.builder().name("Ben").email("ben@example.com").password("x").build());
        cy = userRepository.save(User.builder().name("Cy").email("cy@example.com").password("x").build());
        reset(messagingTemplate);
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void verifyPushed(User to, int times) {
        verify(messagingTemplate, times(times)).convertAndSendToUser(eq(to.getEmail()), eq("/queue/notifications"), any(NotificationResponse.class));
    }

    private List<NotificationResponse> listAll(User user) {
        List<NotificationResponse> all = new ArrayList<>();
        String cursor = null;
        do {
            CursorPageResponse<NotificationResponse> page = notificationService.getNotifications(user.getId(), cursor, 2);
            all.addAll(page.getItems());
            cursor = page.getNextCursor();
        } while (cursor != null);
        return all;
    }

    // ============================================================ when events are handled

    @Test
    void anEventPublishedInsideATransaction_isHandledOnlyAfterItCommits() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));

            // still inside the publisher's transaction: nothing may exist yet, and nothing may have been pushed
            assertThat(notificationRepository.count()).isZero();
            verifyPushed(asha, 0);
        });

        assertThat(notificationRepository.count()).isEqualTo(1);   // committed => handled
        verifyPushed(asha, 1);
    }

    @Test
    void anEventPublishedInATransactionThatRollsBack_createsNothingAndPushesNothing() {
        tx.executeWithoutResult(status -> {
            publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));
            publisher.publishEvent(new PostLikedEvent(1L, asha.getId(), ben.getId()));
            status.setRollbackOnly();
        });

        assertThat(notificationRepository.count()).isZero();
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void anEventPublishedWithNoTransactionAtAll_isStillHandled_becauseSomeServicesAreNotTransactional() {
        // FollowService.followUser and MessageService.sendMessage run without a transaction of their own
        publisher.publishEvent(new MessageSentEvent(900L, ben.getId(), asha.getId()));

        assertThat(notificationRepository.count()).isEqualTo(1);
        verifyPushed(asha, 1);
    }

    @Test
    void aFailureInsideTheHandler_neverReachesTheCodeThatPublishedTheEvent() {
        // a recipient that does not exist: the handler has nothing to do and must not make the publisher fail
        tx.executeWithoutResult(status -> publisher.publishEvent(new PostLikedEvent(1L, 999_999L, ben.getId())));

        assertThat(notificationRepository.count()).isZero();
    }

    // ============================================================ every event type, end to end

    @Test
    void eachKindOfEvent_producesTheRightNotificationForTheRightPerson() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));                               // Asha: Ben followed you
        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), ben.getId()));                            // Asha: Ben liked post 100
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 50L, ben.getId(), null));              // Asha: Ben commented on 100
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 51L, cy.getId(), ben.getId()));        // Ben: Cy replied to you
        publisher.publishEvent(new ExchangeRequestedEvent(7L, ben.getId(), asha.getId()));                      // Asha: Ben wants an exchange
        publisher.publishEvent(new ExchangeAcceptedEvent(7L, ben.getId(), asha.getId()));                       // Ben: Asha accepted
        publisher.publishEvent(new MessageSentEvent(900L, ben.getId(), asha.getId()));                          // Asha: Ben wrote

        List<NotificationResponse> forAsha = listAll(asha);
        assertThat(forAsha).extracting(NotificationResponse::getType).containsExactlyInAnyOrder(
                NotificationType.FOLLOW, NotificationType.LIKE, NotificationType.COMMENT,
                NotificationType.EXCHANGE_REQUEST, NotificationType.MESSAGE);
        assertThat(forAsha).allSatisfy(n -> {
            assertThat(n.getActor().getName()).isEqualTo("Ben");
            assertThat(n.getActor().getEmail()).isNull();
            assertThat(n.isRead()).isFalse();
        });
        NotificationResponse follow = forAsha.stream().filter(n -> n.getType() == NotificationType.FOLLOW).findFirst().orElseThrow();
        assertThat(follow.getTargetType()).isEqualTo(NotificationTargetType.USER);
        assertThat(follow.getTargetId()).isEqualTo(ben.getId());       // opens the follower's profile
        NotificationResponse like = forAsha.stream().filter(n -> n.getType() == NotificationType.LIKE).findFirst().orElseThrow();
        assertThat(like.getTargetType()).isEqualTo(NotificationTargetType.POST);
        assertThat(like.getTargetId()).isEqualTo(100L);

        List<NotificationResponse> forBen = listAll(ben);
        assertThat(forBen).extracting(NotificationResponse::getType)
                .containsExactlyInAnyOrder(NotificationType.REPLY, NotificationType.EXCHANGE_ACCEPTED);
        assertThat(listAll(cy)).isEmpty();                              // Cy only did things, nothing happened to Cy
        verifyPushed(asha, 5);
        verifyPushed(ben, 2);
        verifyPushed(cy, 0);
    }

    @Test
    void aTopLevelComment_doesNotNotifyTheCommenter_andAReplyDoesNotNotifyThePostAuthor() {
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 51L, cy.getId(), ben.getId()));   // Cy replies to Ben on Asha's post

        assertThat(listAll(asha)).isEmpty();                            // the post author is not told about replies
        assertThat(listAll(ben)).hasSize(1);
        assertThat(listAll(cy)).isEmpty();
    }

    // ============================================================ no self-notification

    @Test
    void nobodyIsNotifiedAboutTheirOwnActions_whateverTheEvent() {
        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), asha.getId()));                           // liked my own post
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 50L, asha.getId(), null));             // commented on my own post
        publisher.publishEvent(new CommentAddedEvent(100L, ben.getId(), 51L, asha.getId(), asha.getId()));      // replied to my own comment
        publisher.publishEvent(new UserFollowedEvent(asha.getId(), asha.getId()));
        publisher.publishEvent(new ExchangeRequestedEvent(7L, asha.getId(), asha.getId()));
        publisher.publishEvent(new ExchangeAcceptedEvent(7L, asha.getId(), asha.getId()));
        publisher.publishEvent(new MessageSentEvent(900L, asha.getId(), asha.getId()));

        assertThat(notificationRepository.count()).isZero();
        assertThat(notificationService.getUnreadCount(asha.getId())).isZero();
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    // ============================================================ collapsing duplicates

    @Test
    void likingTheSamePostAgainAndAgain_leavesOneNotification_andPushesOnce() {
        for (int i = 0; i < 4; i++) {   // like, unlike, like, unlike ... each like publishes an event
            publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), ben.getId()));
        }

        assertThat(notificationRepository.count()).isEqualTo(1);
        verifyPushed(asha, 1);
    }

    @Test
    void differentPeopleLikingTheSamePost_andOnePersonLikingDifferentPosts_areSeparateNotifications() {
        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), ben.getId()));
        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), cy.getId()));        // another person
        publisher.publishEvent(new PostLikedEvent(101L, asha.getId(), ben.getId()));       // another post

        assertThat(notificationRepository.count()).isEqualTo(3);
    }

    @Test
    void followingAgainAfterUnfollowing_doesNotNotifyAgain() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));

        assertThat(notificationRepository.count()).isEqualTo(1);
        verifyPushed(asha, 1);
    }

    @Test
    void theDatabaseItselfRefusesASecondLikeNotification_evenWhenTheApplicationCheckIsBypassed() {
        notificationRepository.saveAndFlush(Notification.builder().recipient(asha).actor(ben).type(NotificationType.LIKE)
                .targetType(NotificationTargetType.POST).targetId(100L).dedupeKey("x:LIKE:POST:100").createdAt(LocalDateTime.now()).build());

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(Notification.builder().recipient(asha).actor(ben)
                .type(NotificationType.LIKE).targetType(NotificationTargetType.POST).targetId(100L)
                .dedupeKey("x:LIKE:POST:100").createdAt(LocalDateTime.now()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void severalComments_areSeparateNotifications() {
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 50L, ben.getId(), null));
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 51L, ben.getId(), null));

        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void manyMessagesFromOnePerson_shareOneUnreadNotification_thatKeepsPointingAtTheNewest() {
        publisher.publishEvent(new MessageSentEvent(901L, ben.getId(), asha.getId()));
        publisher.publishEvent(new MessageSentEvent(902L, ben.getId(), asha.getId()));
        publisher.publishEvent(new MessageSentEvent(903L, ben.getId(), asha.getId()));

        List<NotificationResponse> list = listAll(asha);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTargetId()).isEqualTo(903L);
        assertThat(notificationService.getUnreadCount(asha.getId())).isEqualTo(1);

        // each message still reaches a connected client (it replaces the notification with the same id)
        ArgumentCaptor<NotificationResponse> pushed = ArgumentCaptor.forClass(NotificationResponse.class);
        verify(messagingTemplate, times(3)).convertAndSendToUser(eq(asha.getEmail()), eq("/queue/notifications"), pushed.capture());
        assertThat(pushed.getAllValues()).extracting(NotificationResponse::getId).containsOnly(list.get(0).getId());
        assertThat(pushed.getAllValues()).extracting(NotificationResponse::getTargetId).containsExactly(901L, 902L, 903L);
    }

    @Test
    void messagesFromDifferentPeople_areSeparate_andAMessageAfterReadingStartsAFreshOne() {
        publisher.publishEvent(new MessageSentEvent(901L, ben.getId(), asha.getId()));
        publisher.publishEvent(new MessageSentEvent(902L, cy.getId(), asha.getId()));
        assertThat(listAll(asha)).hasSize(2);

        notificationService.markAllRead(asha.getId());
        publisher.publishEvent(new MessageSentEvent(903L, ben.getId(), asha.getId()));

        assertThat(listAll(asha)).hasSize(3);                          // Ben's earlier one was read, so this is new
        assertThat(notificationService.getUnreadCount(asha.getId())).isEqualTo(1);
    }

    // ============================================================ read / unread

    @Test
    void unreadCount_startsAtTheNumberOfNotifications_andDropsAsTheyAreRead() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));
        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), ben.getId()));
        publisher.publishEvent(new CommentAddedEvent(100L, asha.getId(), 50L, cy.getId(), null));
        assertThat(notificationService.getUnreadCount(asha.getId())).isEqualTo(3);

        Long first = listAll(asha).get(0).getId();
        notificationService.markRead(asha.getId(), first);

        assertThat(notificationService.getUnreadCount(asha.getId())).isEqualTo(2);
        assertThat(listAll(asha)).filteredOn(n -> n.getId().equals(first)).singleElement().satisfies(n -> assertThat(n.isRead()).isTrue());
    }

    @Test
    void markRead_isIdempotent() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));
        Long id = listAll(asha).get(0).getId();

        notificationService.markRead(asha.getId(), id);
        notificationService.markRead(asha.getId(), id);   // again: still fine

        assertThat(notificationService.getUnreadCount(asha.getId())).isZero();
    }

    @Test
    void markRead_onSomebodyElsesNotification_is404_andChangesNothing() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));
        Long ashasNotification = listAll(asha).get(0).getId();

        assertThatThrownBy(() -> notificationService.markRead(cy.getId(), ashasNotification))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Notification not found");
        assertThatThrownBy(() -> notificationService.markRead(asha.getId(), 999_999L))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(notificationService.getUnreadCount(asha.getId())).isEqualTo(1);   // untouched
    }

    @Test
    void markAllRead_clearsOnlyTheCallersNotifications() {
        publisher.publishEvent(new UserFollowedEvent(cy.getId(), asha.getId()));
        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), ben.getId()));
        publisher.publishEvent(new UserFollowedEvent(cy.getId(), ben.getId()));

        notificationService.markAllRead(asha.getId());

        assertThat(notificationService.getUnreadCount(asha.getId())).isZero();
        assertThat(notificationService.getUnreadCount(ben.getId())).isEqualTo(1);    // someone else's: untouched
        assertThat(listAll(asha)).allSatisfy(n -> assertThat(n.isRead()).isTrue());
    }

    @Test
    void aNewNotification_afterEverythingWasRead_isUnreadAndCounted() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));
        notificationService.markAllRead(asha.getId());
        assertThat(notificationService.getUnreadCount(asha.getId())).isZero();

        publisher.publishEvent(new PostLikedEvent(100L, asha.getId(), cy.getId()));

        assertThat(notificationService.getUnreadCount(asha.getId())).isEqualTo(1);
    }

    @Test
    void youOnlyEverSeeYourOwnNotifications() {
        publisher.publishEvent(new UserFollowedEvent(ben.getId(), asha.getId()));

        assertThat(listAll(asha)).hasSize(1);
        assertThat(listAll(ben)).isEmpty();
        assertThat(listAll(cy)).isEmpty();
        assertThat(notificationService.getUnreadCount(ben.getId())).isZero();
    }

    // ============================================================ listing and paging on stored rows

    private void store(User recipient, User actor, long targetId, LocalDateTime createdAt) {
        notificationRepository.saveAndFlush(Notification.builder().recipient(recipient).actor(actor).type(NotificationType.COMMENT)
                .targetType(NotificationTargetType.POST).targetId(targetId).createdAt(createdAt).build());
    }

    @Test
    void list_isNewestFirst_andPagingVisitsEveryNotificationOnce_includingTiesOnOneTimestamp() {
        LocalDateTime same = LocalDateTime.of(2026, 11, 4, 12, 0);
        store(asha, ben, 1, same.minusHours(5));
        store(asha, ben, 2, same);          // 2, 3, 4 share one timestamp: the id breaks the tie
        store(asha, cy, 3, same);
        store(asha, ben, 4, same);
        store(asha, cy, 5, same.plusHours(2));

        List<Long> order = listAll(asha).stream().map(NotificationResponse::getTargetId).toList();

        assertThat(order).containsExactly(5L, 4L, 3L, 2L, 1L);
        assertThat(notificationService.getNotifications(asha.getId(), null, 5).getNextCursor()).isNull();
        assertThat(notificationService.getNotifications(asha.getId(), null, 4).getNextCursor()).isNotNull();
    }

    @Test
    void list_aCollapsedMessageNotificationMovesBackToTheTop() {
        publisher.publishEvent(new MessageSentEvent(901L, ben.getId(), asha.getId()));
        // make that message notification old, then let something newer arrive after it
        Notification message = notificationRepository.findAll().get(0);
        message.setCreatedAt(LocalDateTime.now().minusHours(2));
        notificationRepository.saveAndFlush(message);
        store(asha, cy, 7, LocalDateTime.now().minusHours(1));
        assertThat(listAll(asha).get(0).getType()).isEqualTo(NotificationType.COMMENT);   // the comment is on top now

        publisher.publishEvent(new MessageSentEvent(902L, ben.getId(), asha.getId()));   // Ben writes again, just now

        List<NotificationResponse> list = listAll(asha);
        assertThat(list).hasSize(2);                                       // still one message notification, not two
        assertThat(list.get(0).getType()).isEqualTo(NotificationType.MESSAGE);   // refreshed, so it is on top again
        assertThat(list.get(0).getTargetId()).isEqualTo(902L);
    }
}
