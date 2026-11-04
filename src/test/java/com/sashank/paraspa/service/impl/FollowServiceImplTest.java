package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.FollowRequestResponse;
import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.FollowStatus;
import com.sashank.paraspa.event.FollowRequestAcceptedEvent;
import com.sashank.paraspa.event.FollowRequestedEvent;
import com.sashank.paraspa.event.UserFollowedEvent;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ForbiddenException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.AccessPolicy;
import com.sashank.paraspa.service.BlockService;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock private FollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @Mock private BlockService blockService;
    @Mock private AccessPolicy accessPolicy;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DtoMapper dtoMapper;

    @InjectMocks
    private FollowServiceImpl followService;

    private final User me = User.builder().id(1L).build();
    private final User publicUser = User.builder().id(2L).build();
    private final User privateUser = User.builder().id(3L).isPrivate(true).build();

    private void stubUsers(User target) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    }

    private void stubSave() {
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toFollowResponse(any(Follow.class))).thenReturn(FollowResponse.builder().build());
    }

    private Follow savedFollow() {
        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());
        return captor.getValue();
    }

    // ================================================================ following a public account

    @Test
    void followUser_publicAccount_isAcceptedAtOnce_andPublishesAFollowedEvent() {
        stubUsers(publicUser);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        stubSave();

        followService.followUser(1L, 2L);

        Follow follow = savedFollow();
        assertThat(follow.getFollower()).isEqualTo(me);
        assertThat(follow.getFollowing()).isEqualTo(publicUser);
        assertThat(follow.getStatus()).isEqualTo(FollowStatus.ACCEPTED);
        verify(eventPublisher).publishEvent(new UserFollowedEvent(1L, 2L));
        verify(eventPublisher, never()).publishEvent(any(FollowRequestedEvent.class));
    }

    // ================================================================ following a private account

    @Test
    void followUser_privateAccount_createsAPendingRequest_andPublishesARequestedEvent() {
        stubUsers(privateUser);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 3L)).thenReturn(Optional.empty());
        stubSave();

        followService.followUser(1L, 3L);

        assertThat(savedFollow().getStatus()).isEqualTo(FollowStatus.PENDING);
        verify(eventPublisher).publishEvent(new FollowRequestedEvent(1L, 3L));
        // the owner is asked; nobody is told "X follows you" yet
        verify(eventPublisher, never()).publishEvent(any(UserFollowedEvent.class));
    }

    @Test
    void followUser_askingAgainWhileStillPending_changesNothingAndPublishesNothing() {
        Follow pending = Follow.builder().id(10L).follower(me).following(privateUser).status(FollowStatus.PENDING).build();
        stubUsers(privateUser);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 3L)).thenReturn(Optional.of(pending));
        when(dtoMapper.toFollowResponse(pending)).thenReturn(FollowResponse.builder().status(FollowStatus.PENDING).build());

        FollowResponse response = followService.followUser(1L, 3L);

        assertThat(response.getStatus()).isEqualTo(FollowStatus.PENDING);
        verify(followRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ================================================================ idempotency and races

    @Test
    void followUser_alreadyFollowing_isIdempotent() {
        Follow existing = Follow.builder().id(10L).follower(me).following(publicUser).status(FollowStatus.ACCEPTED).build();
        FollowResponse mapped = FollowResponse.builder().id(10L).build();
        stubUsers(publicUser);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(existing));
        when(dtoMapper.toFollowResponse(existing)).thenReturn(mapped);

        assertThat(followService.followUser(1L, 2L)).isSameAs(mapped);

        verify(followRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void followUser_aSimultaneousRequestWinsTheRace_returnsItsRow_andPublishesNothing() {
        Follow winner = Follow.builder().id(11L).follower(me).following(publicUser).build();
        FollowResponse mapped = FollowResponse.builder().id(11L).build();
        stubUsers(publicUser);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty(), Optional.of(winner));
        when(followRepository.save(any(Follow.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(dtoMapper.toFollowResponse(winner)).thenReturn(mapped);

        assertThat(followService.followUser(1L, 2L)).isSameAs(mapped);

        verify(eventPublisher, never()).publishEvent(any(Object.class));   // the request that really inserted it publishes
    }

    @Test
    void followUser_rethrowsAnInsertFailureThatIsNotARace() {
        stubUsers(publicUser);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("something else");
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(Follow.class))).thenThrow(failure);

        assertThatThrownBy(() -> followService.followUser(1L, 2L)).isSameAs(failure);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ================================================================ rejected follows

    @Test
    void followUser_yourself_is400() {
        assertThatThrownBy(() -> followService.followUser(1L, 1L))
                .isInstanceOf(BadRequestException.class).hasMessage("You cannot follow yourself");

        verify(followRepository, never()).save(any());
    }

    @Test
    void followUser_missingTarget_is404() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.followUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
    }

    @Test
    void followUser_acrossABlock_isRefused_whicheverWayItWasBlocked() {
        stubUsers(publicUser);
        doThrow(new BadRequestException("You have blocked this user. Unblock them first."))
                .when(blockService).requireNoBlock(1L, 2L);

        assertThatThrownBy(() -> followService.followUser(1L, 2L)).isInstanceOf(BadRequestException.class);

        verify(followRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void followUser_whenTheOtherPersonBlockedYou_looksLikeAMissingUser() {
        stubUsers(publicUser);
        doThrow(new ResourceNotFoundException("User not found")).when(blockService).requireNoBlock(1L, 2L);

        assertThatThrownBy(() -> followService.followUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");

        verify(followRepository, never()).save(any());
    }

    // ================================================================ unfollow

    @Test
    void unfollowUser_deletesAnAcceptedFollow() {
        Follow follow = Follow.builder().id(10L).status(FollowStatus.ACCEPTED).build();
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(follow));

        followService.unfollowUser(1L, 2L);

        verify(followRepository).delete(follow);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unfollowUser_alsoCancelsAPendingRequest() {
        Follow pending = Follow.builder().id(10L).status(FollowStatus.PENDING).build();
        when(userRepository.existsById(3L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 3L)).thenReturn(Optional.of(pending));

        followService.unfollowUser(1L, 3L);

        verify(followRepository).delete(pending);
    }

    @Test
    void unfollowUser_isIdempotent_whenNotFollowing() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());

        followService.unfollowUser(1L, 2L);

        verify(followRepository, never()).delete(any());
    }

    @Test
    void unfollowUser_missingTarget_is404() {
        when(userRepository.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> followService.unfollowUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
    }

    // ================================================================ follower / following lists

    @Test
    void getFollowers_returnsAcceptedFollowersAsPublicUsers_forTheRequestedPage() {
        User follower = User.builder().id(5L).build();
        Follow follow = Follow.builder().follower(follower).following(publicUser).build();
        UserResponse mapped = UserResponse.builder().id(5L).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(publicUser));
        when(followRepository.findAcceptedFollowers(eq(2L), eq(1L), any(Pageable.class))).thenReturn(List.of(follow));
        when(dtoMapper.toPublicUserResponse(follower)).thenReturn(mapped);

        assertThat(followService.getFollowers(1L, 2L, 3, 40)).containsExactly(mapped);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(followRepository).findAcceptedFollowers(eq(2L), eq(1L), pageable.capture());   // the viewer is passed: blocked users are filtered in SQL
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(40);
        verify(accessPolicy).requireCanSeePostsOf(1L, publicUser);
    }

    @Test
    void getFollowing_returnsTheFollowedUsers() {
        User followed = User.builder().id(6L).build();
        Follow follow = Follow.builder().follower(publicUser).following(followed).build();
        UserResponse mapped = UserResponse.builder().id(6L).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(publicUser));
        when(followRepository.findAcceptedFollowing(eq(2L), eq(1L), any(Pageable.class))).thenReturn(List.of(follow));
        when(dtoMapper.toPublicUserResponse(followed)).thenReturn(mapped);

        assertThat(followService.getFollowing(1L, 2L, 0, 20)).containsExactly(mapped);
    }

    @Test
    void followLists_ofAPrivateAccountTheViewerDoesNotFollow_areForbidden_andNothingIsQueried() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(privateUser));
        doThrow(new ForbiddenException("This account is private")).when(accessPolicy).requireCanSeePostsOf(1L, privateUser);

        assertThatThrownBy(() -> followService.getFollowers(1L, 3L, 0, 20)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> followService.getFollowing(1L, 3L, 0, 20)).isInstanceOf(ForbiddenException.class);

        verify(followRepository, never()).findAcceptedFollowers(any(), any(), any());
        verify(followRepository, never()).findAcceptedFollowing(any(), any(), any());
    }

    @Test
    void followLists_ofAUserBlockedWithTheViewer_look404() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(publicUser));
        doThrow(new ResourceNotFoundException("User not found")).when(accessPolicy).requireCanSeePostsOf(1L, publicUser);

        assertThatThrownBy(() -> followService.getFollowers(1L, 2L, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void followLists_missingUserIs404_andBadPagingIs400() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> followService.getFollowers(1L, 9L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");

        when(userRepository.findById(2L)).thenReturn(Optional.of(publicUser));
        assertThatThrownBy(() -> followService.getFollowers(1L, 2L, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> followService.getFollowing(1L, 2L, -1, 20)).isInstanceOf(BadRequestException.class);
    }

    // ================================================================ follow status

    private void stubStatus(Follow mine, boolean theyFollowMe) {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.ofNullable(mine));
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(2L, 1L, FollowStatus.ACCEPTED)).thenReturn(theyFollowMe);
        when(followRepository.countByFollowingIdAndStatus(2L, FollowStatus.ACCEPTED)).thenReturn(5L);
        when(followRepository.countByFollowerIdAndStatus(2L, FollowStatus.ACCEPTED)).thenReturn(3L);
    }

    @Test
    void getFollowStatus_anAcceptedFollow_isFollowedByMe() {
        stubStatus(Follow.builder().status(FollowStatus.ACCEPTED).build(), true);

        FollowStatusResponse status = followService.getFollowStatus(1L, 2L);

        assertThat(status.isFollowedByMe()).isTrue();
        assertThat(status.isFollowRequestedByMe()).isFalse();
        assertThat(status.isFollowingMe()).isTrue();
        assertThat(status.getFollowersCount()).isEqualTo(5L);   // counts are accepted follows only
        assertThat(status.getFollowingCount()).isEqualTo(3L);
    }

    @Test
    void getFollowStatus_aPendingRequest_isRequested_notFollowed() {
        stubStatus(Follow.builder().status(FollowStatus.PENDING).build(), false);

        FollowStatusResponse status = followService.getFollowStatus(1L, 2L);

        assertThat(status.isFollowedByMe()).isFalse();
        assertThat(status.isFollowRequestedByMe()).isTrue();
    }

    @Test
    void getFollowStatus_noFollow_isNeither() {
        stubStatus(null, false);

        FollowStatusResponse status = followService.getFollowStatus(1L, 2L);

        assertThat(status.isFollowedByMe()).isFalse();
        assertThat(status.isFollowRequestedByMe()).isFalse();
        assertThat(status.isFollowingMe()).isFalse();
    }

    @Test
    void getFollowStatus_missingOrBlockedUser_is404() {
        when(userRepository.existsById(9L)).thenReturn(false);
        assertThatThrownBy(() -> followService.getFollowStatus(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);

        when(userRepository.existsById(2L)).thenReturn(true);
        doThrow(new ResourceNotFoundException("User not found")).when(accessPolicy).requireUserVisible(1L, 2L);
        assertThatThrownBy(() -> followService.getFollowStatus(1L, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================ follow requests (private accounts)

    @Test
    void getFollowRequests_listsThePendingRequestsToMe_withTheRequesterAsAPublicUser() {
        User requester = User.builder().id(5L).build();
        LocalDateTime when = LocalDateTime.of(2026, 11, 4, 12, 0);
        Follow pending = Follow.builder().id(10L).follower(requester).following(privateUser).status(FollowStatus.PENDING).createdAt(when).build();
        UserResponse mapped = UserResponse.builder().id(5L).build();
        when(followRepository.findByFollowingIdAndStatusOrderByCreatedAtDescIdDesc(eq(3L), eq(FollowStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(pending));
        when(dtoMapper.toPublicUserResponse(requester)).thenReturn(mapped);

        List<FollowRequestResponse> requests = followService.getFollowRequests(3L, 0, 20);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getId()).isEqualTo(10L);
        assertThat(requests.get(0).getRequester()).isSameAs(mapped);
        assertThat(requests.get(0).getCreatedAt()).isEqualTo(when);
    }

    @Test
    void getFollowRequests_rejectsBadPaging() {
        assertThatThrownBy(() -> followService.getFollowRequests(3L, 0, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptFollowRequest_makesItAnAcceptedFollow_andTellsTheRequester() {
        User requester = User.builder().id(5L).build();
        Follow pending = Follow.builder().id(10L).follower(requester).following(privateUser).status(FollowStatus.PENDING).build();
        when(followRepository.findByIdAndFollowingIdAndStatus(10L, 3L, FollowStatus.PENDING)).thenReturn(Optional.of(pending));

        followService.acceptFollowRequest(3L, 10L);

        assertThat(pending.getStatus()).isEqualTo(FollowStatus.ACCEPTED);
        verify(followRepository).save(pending);
        verify(eventPublisher).publishEvent(new FollowRequestAcceptedEvent(5L, 3L));
    }

    @Test
    void acceptFollowRequest_aRequestNotAddressedToMeOrNotPending_is404_andChangesNothing() {
        // the lookup is scoped to the owner and to PENDING, so someone else's or an already accepted request matches nothing
        when(followRepository.findByIdAndFollowingIdAndStatus(10L, 4L, FollowStatus.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.acceptFollowRequest(4L, 10L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Follow request not found");

        verify(followRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectFollowRequest_deletesIt_andTellsNobody() {
        Follow pending = Follow.builder().id(10L).follower(User.builder().id(5L).build()).following(privateUser)
                .status(FollowStatus.PENDING).build();
        when(followRepository.findByIdAndFollowingIdAndStatus(10L, 3L, FollowStatus.PENDING)).thenReturn(Optional.of(pending));

        followService.rejectFollowRequest(3L, 10L);

        verify(followRepository).delete(pending);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectFollowRequest_aRequestNotAddressedToMe_is404() {
        when(followRepository.findByIdAndFollowingIdAndStatus(10L, 4L, FollowStatus.PENDING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.rejectFollowRequest(4L, 10L)).isInstanceOf(ResourceNotFoundException.class);

        verify(followRepository, never()).delete(any());
    }
}
