package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatsResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.AlreadyExistsException;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void followUser_shouldThrowBadRequestException_whenFollowingSelf() {
        assertThatThrownBy(() -> followService.followUser(1L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot follow yourself");

        verify(followRepository, never()).save(any());
    }

    @Test
    void followUser_shouldThrowResourceNotFoundException_whenTargetMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.followUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(followRepository, never()).save(any());
    }

    @Test
    void followUser_shouldThrowAlreadyExistsException_whenAlreadyFollowing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> followService.followUser(1L, 2L))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("You are already following this user");

        verify(followRepository, never()).save(any());
    }

    @Test
    void followUser_shouldSaveFollowWithCorrectUsers() {
        User follower = User.builder().id(1L).build();
        User target = User.builder().id(2L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toFollowResponse(any(Follow.class))).thenReturn(FollowResponse.builder().build());

        followService.followUser(1L, 2L);

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());
        assertThat(captor.getValue().getFollower()).isEqualTo(follower);
        assertThat(captor.getValue().getFollowing()).isEqualTo(target);
    }

    @Test
    void unfollowUser_shouldThrowResourceNotFoundException_whenNotFollowing() {
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.unfollowUser(1L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("You are not following this user");

        verify(followRepository, never()).delete(any());
    }

    @Test
    void unfollowUser_shouldDeleteFollow() {
        Follow follow = Follow.builder().id(10L).build();
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(follow));

        followService.unfollowUser(1L, 2L);

        verify(followRepository).delete(follow);
    }

    @Test
    void getFollowers_shouldReturnFollowerUsers() {
        User follower = User.builder().id(1L).build();
        Follow follow = Follow.builder().follower(follower).following(User.builder().id(2L).build()).build();
        UserResponse mapped = UserResponse.builder().id(1L).build();

        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.findByFollowingIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(follow));
        when(dtoMapper.toPublicUserResponse(follower)).thenReturn(mapped);

        assertThat(followService.getFollowers(2L)).containsExactly(mapped);
    }

    @Test
    void getFollowing_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> followService.getFollowing(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void getFollowStats_shouldReturnCountsAndViewerState() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.countByFollowingId(2L)).thenReturn(5L);
        when(followRepository.countByFollowerId(2L)).thenReturn(3L);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        FollowStatsResponse stats = followService.getFollowStats(1L, 2L);

        assertThat(stats.getUserId()).isEqualTo(2L);
        assertThat(stats.getFollowersCount()).isEqualTo(5L);
        assertThat(stats.getFollowingCount()).isEqualTo(3L);
        assertThat(stats.isFollowedByMe()).isTrue();
    }
}
