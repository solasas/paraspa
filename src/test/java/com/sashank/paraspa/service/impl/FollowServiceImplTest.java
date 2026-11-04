package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private FollowServiceImpl followService;

    private final User me = User.builder().id(1L).build();
    private final User target = User.builder().id(2L).build();

    private void stubBothUsers() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
    }

    // ---------------------------------------------------------------- follow

    @Test
    void followUser_shouldSaveFollowWithCorrectUsers() {
        stubBothUsers();
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toFollowResponse(any(Follow.class))).thenReturn(FollowResponse.builder().build());

        followService.followUser(1L, 2L);

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());
        assertThat(captor.getValue().getFollower()).isEqualTo(me);
        assertThat(captor.getValue().getFollowing()).isEqualTo(target);
    }

    @Test
    void followUser_shouldBeIdempotent_whenAlreadyFollowing() {
        Follow existing = Follow.builder().id(10L).follower(me).following(target).build();
        FollowResponse mapped = FollowResponse.builder().id(10L).build();

        stubBothUsers();
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(existing));
        when(dtoMapper.toFollowResponse(existing)).thenReturn(mapped);

        FollowResponse response = followService.followUser(1L, 2L);

        assertThat(response).isSameAs(mapped);
        verify(followRepository, never()).save(any());
    }

    @Test
    void followUser_shouldReturnTheWinnersRow_whenAConcurrentRequestInsertsFirst() {
        Follow winner = Follow.builder().id(11L).follower(me).following(target).build();
        FollowResponse mapped = FollowResponse.builder().id(11L).build();

        stubBothUsers();
        // first lookup: nothing yet; the unique constraint then rejects our insert; second lookup: the winner
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(followRepository.save(any(Follow.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(dtoMapper.toFollowResponse(winner)).thenReturn(mapped);

        assertThat(followService.followUser(1L, 2L)).isSameAs(mapped);
    }

    @Test
    void followUser_shouldRethrow_whenInsertFailsForAnotherReason() {
        stubBothUsers();
        DataIntegrityViolationException failure = new DataIntegrityViolationException("something else");
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(followRepository.save(any(Follow.class))).thenThrow(failure);

        assertThatThrownBy(() -> followService.followUser(1L, 2L)).isSameAs(failure);
    }

    @Test
    void followUser_shouldThrowBadRequestException_whenFollowingSelf() {
        assertThatThrownBy(() -> followService.followUser(1L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot follow yourself");

        verify(followRepository, never()).save(any());
    }

    @Test
    void followUser_shouldThrowResourceNotFoundException_whenTargetMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.followUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(followRepository, never()).save(any());
    }

    // -------------------------------------------------------------- unfollow

    @Test
    void unfollowUser_shouldDeleteTheFollow() {
        Follow follow = Follow.builder().id(10L).build();
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(follow));

        followService.unfollowUser(1L, 2L);

        verify(followRepository).delete(follow);
    }

    @Test
    void unfollowUser_shouldDoNothing_whenNotFollowing() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());

        followService.unfollowUser(1L, 2L);

        verify(followRepository, never()).delete(any());
    }

    @Test
    void unfollowUser_shouldThrowResourceNotFoundException_whenTargetMissing() {
        when(userRepository.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> followService.unfollowUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(followRepository, never()).delete(any());
    }

    // ------------------------------------------------------------ followers

    @Test
    void getFollowers_shouldReturnFollowerUsersForTheRequestedPage() {
        User follower = User.builder().id(5L).build();
        Follow follow = Follow.builder().follower(follower).following(target).build();
        UserResponse mapped = UserResponse.builder().id(5L).build();

        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowingIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(follow));
        when(dtoMapper.toPublicUserResponse(follower)).thenReturn(mapped);

        assertThat(followService.getFollowers(2L, 3, 40)).containsExactly(mapped);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(followRepository).findByFollowingIdOrderByCreatedAtDescIdDesc(eq(2L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(40);
    }

    @Test
    void getFollowing_shouldReturnFollowedUsersForTheRequestedPage() {
        User followed = User.builder().id(6L).build();
        Follow follow = Follow.builder().follower(target).following(followed).build();
        UserResponse mapped = UserResponse.builder().id(6L).build();

        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowerIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(follow));
        when(dtoMapper.toPublicUserResponse(followed)).thenReturn(mapped);

        assertThat(followService.getFollowing(2L, 0, 20)).containsExactly(mapped);
    }

    @Test
    void followLists_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> followService.getFollowers(9L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
        assertThatThrownBy(() -> followService.getFollowing(9L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
    }

    @Test
    void followLists_shouldRejectInvalidPaging() {
        when(userRepository.existsById(2L)).thenReturn(true);

        assertThatThrownBy(() -> followService.getFollowers(2L, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> followService.getFollowing(2L, -1, 20)).isInstanceOf(BadRequestException.class);

        verify(followRepository, never()).findByFollowingIdOrderByCreatedAtDescIdDesc(any(), any());
        verify(followRepository, never()).findByFollowerIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    // ---------------------------------------------------------------- status

    @Test
    void getFollowStatus_shouldReportBothDirectionsAndCounts() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingId(2L, 1L)).thenReturn(false);
        when(followRepository.countByFollowingId(2L)).thenReturn(5L);
        when(followRepository.countByFollowerId(2L)).thenReturn(3L);

        FollowStatusResponse status = followService.getFollowStatus(1L, 2L);

        assertThat(status.getUserId()).isEqualTo(2L);
        assertThat(status.isFollowedByMe()).isTrue();
        assertThat(status.isFollowingMe()).isFalse();
        assertThat(status.getFollowersCount()).isEqualTo(5L);
        assertThat(status.getFollowingCount()).isEqualTo(3L);
    }

    @Test
    void getFollowStatus_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> followService.getFollowStatus(1L, 9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }
}
