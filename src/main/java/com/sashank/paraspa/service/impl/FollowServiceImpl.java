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
import com.sashank.paraspa.service.FollowService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public FollowResponse followUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("You cannot follow yourself");
        }

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (followRepository.existsByFollowerIdAndFollowingId(userId, targetUserId)) {
            throw new AlreadyExistsException("You are already following this user");
        }

        Follow follow = Follow.builder()
                .follower(follower)
                .following(target)
                .build();

        follow = followRepository.save(follow);
        return dtoMapper.toFollowResponse(follow);
    }

    @Override
    public void unfollowUser(Long userId, Long targetUserId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(userId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("You are not following this user"));

        followRepository.delete(follow);
    }

    @Override
    public List<UserResponse> getFollowers(Long targetUserId) {
        ensureUserExists(targetUserId);
        return followRepository.findByFollowingIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(follow -> dtoMapper.toPublicUserResponse(follow.getFollower()))
                .toList();
    }

    @Override
    public List<UserResponse> getFollowing(Long targetUserId) {
        ensureUserExists(targetUserId);
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(follow -> dtoMapper.toPublicUserResponse(follow.getFollowing()))
                .toList();
    }

    @Override
    public FollowStatsResponse getFollowStats(Long viewerId, Long targetUserId) {
        ensureUserExists(targetUserId);
        return FollowStatsResponse.builder()
                .userId(targetUserId)
                .followersCount(followRepository.countByFollowingId(targetUserId))
                .followingCount(followRepository.countByFollowerId(targetUserId))
                .followedByMe(followRepository.existsByFollowerIdAndFollowingId(viewerId, targetUserId))
                .build();
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }
}
