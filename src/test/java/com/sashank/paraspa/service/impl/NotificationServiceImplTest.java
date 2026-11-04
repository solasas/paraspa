package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.entity.Notification;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.NotificationRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.NotificationService;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private DtoMapper dtoMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private final User recipient = User.builder().id(2L).email("recipient@example.com").build();
    private final User actor = User.builder().id(5L).email("actor@example.com").build();

    @BeforeEach
    void mapperAndUsers() {
        org.mockito.Mockito.lenient().when(dtoMapper.toNotificationResponse(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            return NotificationResponse.builder().id(n.getId()).type(n.getType()).targetType(n.getTargetType())
                    .targetId(n.getTargetId()).read(n.isRead()).createdAt(n.getCreatedAt()).build();
        });
    }

    private void stubUsers() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(userRepository.findById(5L)).thenReturn(Optional.of(actor));
    }

    private void stubSave() {
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(900L);
            n.setCreatedAt(LocalDateTime.of(2026, 11, 4, 12, 0));
            return n;
        });
    }

    private ArgumentCaptor<Notification> saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        return captor;
    }

    // ============================================================ create: who is notified

    @Test
    void create_neverNotifiesSomeoneAboutTheirOwnAction() {
        Optional<NotificationService.Created> result = notificationService.create(
                7L, 7L, NotificationType.LIKE, NotificationTargetType.POST, 100L);

        assertThat(result).isEmpty();
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    void create_selfNotificationIsSkippedForEveryType() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(notificationService.create(7L, 7L, type, NotificationTargetType.POST, 1L))
                    .as("type %s", type).isEmpty();
        }
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void create_ignoresMissingIds() {
        assertThat(notificationService.create(null, 5L, NotificationType.LIKE, NotificationTargetType.POST, 1L)).isEmpty();
        assertThat(notificationService.create(2L, null, NotificationType.LIKE, NotificationTargetType.POST, 1L)).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void create_doesNothingWhenTheRecipientOrTheActorNoLongerExists() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());
        when(userRepository.findById(5L)).thenReturn(Optional.of(actor));

        assertThat(notificationService.create(2L, 5L, NotificationType.COMMENT, NotificationTargetType.POST, 100L)).isEmpty();

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_storesAnUnreadNotificationAndReturnsWhereToPushIt() {
        stubUsers();
        stubSave();

        NotificationService.Created created = notificationService
                .create(2L, 5L, NotificationType.COMMENT, NotificationTargetType.POST, 100L).orElseThrow();

        Notification n = saved().getValue();
        assertThat(n.getRecipient()).isEqualTo(recipient);
        assertThat(n.getActor()).isEqualTo(actor);
        assertThat(n.getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(n.getTargetType()).isEqualTo(NotificationTargetType.POST);
        assertThat(n.getTargetId()).isEqualTo(100L);
        assertThat(n.isRead()).isFalse();
        assertThat(created.recipientEmail()).isEqualTo("recipient@example.com");
        assertThat(created.notification().getId()).isEqualTo(900L);
        assertThat(created.notification().isRead()).isFalse();
    }

    @Test
    void create_commentsAreNeverCollapsed_eachOneIsNews() {
        stubUsers();
        stubSave();

        notificationService.create(2L, 5L, NotificationType.COMMENT, NotificationTargetType.POST, 100L);
        notificationService.create(2L, 5L, NotificationType.COMMENT, NotificationTargetType.POST, 100L);

        verify(notificationRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Notification.class));
        verify(notificationRepository, never()).existsByRecipientIdAndDedupeKey(anyLong(), anyString());
    }

    // ============================================================ create: collapsing duplicate likes

    @Test
    void create_aLikeIsStoredWithADedupeKeyBuiltFromActorTypeAndTarget() {
        stubUsers();
        stubSave();
        when(notificationRepository.existsByRecipientIdAndDedupeKey(2L, "5:LIKE:POST:100")).thenReturn(false);

        notificationService.create(2L, 5L, NotificationType.LIKE, NotificationTargetType.POST, 100L);

        assertThat(saved().getValue().getDedupeKey()).isEqualTo("5:LIKE:POST:100");
    }

    @Test
    void create_aSecondLikeOfTheSamePostByTheSamePersonIsCollapsed() {
        stubUsers();
        when(notificationRepository.existsByRecipientIdAndDedupeKey(2L, "5:LIKE:POST:100")).thenReturn(true);

        assertThat(notificationService.create(2L, 5L, NotificationType.LIKE, NotificationTargetType.POST, 100L)).isEmpty();

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_likesOfDifferentPostsOrByDifferentPeopleAreDifferentNotifications() {
        stubUsers();
        stubSave();
        // same actor, another post; and (below) another person liking the first post
        when(notificationRepository.existsByRecipientIdAndDedupeKey(2L, "5:LIKE:POST:101")).thenReturn(false);

        assertThat(notificationService.create(2L, 5L, NotificationType.LIKE, NotificationTargetType.POST, 101L)).isPresent();
        assertThat(saved().getValue().getDedupeKey()).isEqualTo("5:LIKE:POST:101");
    }

    @Test
    void create_aRepeatedFollowIsCollapsedToo() {
        stubUsers();
        when(notificationRepository.existsByRecipientIdAndDedupeKey(2L, "5:FOLLOW:USER:5")).thenReturn(true);

        assertThat(notificationService.create(2L, 5L, NotificationType.FOLLOW, NotificationTargetType.USER, 5L)).isEmpty();

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_aSimultaneousDuplicateLikeSurfacesAsAUniqueViolation_leftToTheCaller() {
        // two identical requests passed the "exists" check together; the unique constraint stops the second one
        stubUsers();
        when(notificationRepository.existsByRecipientIdAndDedupeKey(2L, "5:LIKE:POST:100")).thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> notificationService.create(2L, 5L, NotificationType.LIKE, NotificationTargetType.POST, 100L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ============================================================ create: collapsing messages

    @Test
    void create_aMessageFromSomeoneWithAnUnreadMessageNotificationUpdatesThatNotification() {
        stubUsers();
        LocalDateTime old = LocalDateTime.of(2026, 11, 1, 9, 0);
        Notification unread = Notification.builder().id(40L).recipient(recipient).actor(actor).type(NotificationType.MESSAGE)
                .targetType(NotificationTargetType.MESSAGE).targetId(111L).createdAt(old).build();
        when(notificationRepository.findFirstByRecipientIdAndActorIdAndTypeAndReadFalseOrderByIdDesc(2L, 5L, NotificationType.MESSAGE))
                .thenReturn(Optional.of(unread));
        when(notificationRepository.save(unread)).thenReturn(unread);

        NotificationService.Created created = notificationService
                .create(2L, 5L, NotificationType.MESSAGE, NotificationTargetType.MESSAGE, 222L).orElseThrow();

        assertThat(unread.getTargetId()).isEqualTo(222L);                // now points at the newest message
        assertThat(unread.getCreatedAt()).isAfter(old);                  // and moves back to the top of the list
        assertThat(unread.isRead()).isFalse();
        assertThat(created.notification().getId()).isEqualTo(40L);       // the same notification, not a second one
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_aMessageWhenEverythingFromThatPersonIsAlreadyReadStartsANewNotification() {
        stubUsers();
        stubSave();
        when(notificationRepository.findFirstByRecipientIdAndActorIdAndTypeAndReadFalseOrderByIdDesc(2L, 5L, NotificationType.MESSAGE))
                .thenReturn(Optional.empty());

        NotificationService.Created created = notificationService
                .create(2L, 5L, NotificationType.MESSAGE, NotificationTargetType.MESSAGE, 222L).orElseThrow();

        assertThat(created.notification().getId()).isEqualTo(900L);
        assertThat(saved().getValue().getDedupeKey()).isNull();
    }

    // ============================================================ listing

    private static Notification notification(long id, LocalDateTime createdAt) {
        return Notification.builder().id(id).type(NotificationType.LIKE).targetType(NotificationTargetType.POST)
                .targetId(1L).createdAt(createdAt).build();
    }

    @Test
    void getNotifications_firstPageFetchesOneExtraRowAndPointsTheCursorAtTheLastReturnedOne() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(notification(30, t.plusMinutes(3)), notification(20, t.plusMinutes(2)), notification(10, t.plusMinutes(1))));

        CursorPageResponse<NotificationResponse> page = notificationService.getNotifications(2L, null, 2);

        assertThat(page.getItems()).extracting(NotificationResponse::getId).containsExactly(30L, 20L);
        CursorUtil.Keyset next = CursorUtil.decodeKeyset(page.getNextCursor());
        assertThat(next.id()).isEqualTo(20L);                           // not the dropped extra row
        assertThat(next.createdAt()).isEqualTo(t.plusMinutes(2));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDescIdDesc(eq(2L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void getNotifications_lastPageHasNoCursor() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(notification(20, LocalDateTime.of(2026, 11, 4, 12, 0))));

        assertThat(notificationService.getNotifications(2L, null, 20).getNextCursor()).isNull();
    }

    @Test
    void getNotifications_continuesAfterTheCursor() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0);
        when(notificationRepository.findByRecipientIdBefore(eq(2L), eq(t), eq(20L), any(Pageable.class)))
                .thenReturn(List.of(notification(10, t.minusMinutes(1))));

        CursorPageResponse<NotificationResponse> page = notificationService.getNotifications(2L, CursorUtil.encode(t, 20L), 20);

        assertThat(page.getItems()).extracting(NotificationResponse::getId).containsExactly(10L);
        verify(notificationRepository, never()).findByRecipientIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void getNotifications_isEmptyForAUserWithNoNotifications() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class))).thenReturn(List.of());

        CursorPageResponse<NotificationResponse> page = notificationService.getNotifications(2L, null, 20);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void getNotifications_rejectsABadCursorAndBadSizes() {
        assertThatThrownBy(() -> notificationService.getNotifications(2L, "garbage!", 20))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> notificationService.getNotifications(2L, null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> notificationService.getNotifications(2L, null, 101)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(notificationRepository);
    }

    // ============================================================ read / unread

    @Test
    void getUnreadCount_countsOnlyTheUsersUnreadNotifications() {
        when(notificationRepository.countByRecipientIdAndReadFalse(2L)).thenReturn(7L);

        assertThat(notificationService.getUnreadCount(2L)).isEqualTo(7L);
    }

    @Test
    void markRead_marksOneOfTheUsersNotifications() {
        when(notificationRepository.markRead(40L, 2L)).thenReturn(1);

        notificationService.markRead(2L, 40L);

        verify(notificationRepository).markRead(40L, 2L);
    }

    @Test
    void markRead_isIdempotent_markingAnAlreadyReadNotificationIsNotAnError() {
        when(notificationRepository.markRead(40L, 2L)).thenReturn(1, 1);   // matched both times, changed once

        notificationService.markRead(2L, 40L);
        notificationService.markRead(2L, 40L);
    }

    @Test
    void markRead_somebodyElsesOrMissingNotificationIsA404() {
        // the repository only matches rows of the given recipient, so someone else's id matches nothing
        when(notificationRepository.markRead(40L, 3L)).thenReturn(0);

        assertThatThrownBy(() -> notificationService.markRead(3L, 40L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Notification not found");
    }

    @Test
    void markAllRead_marksEverythingOfTheUserOnly() {
        notificationService.markAllRead(2L);

        verify(notificationRepository).markAllRead(2L);
    }
}
