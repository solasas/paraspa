package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.entity.Follow;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.event.UserFollowedEvent;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.FollowService;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private DtoMapper dtoMapper;

    // Deliberately not @Transactional: if two identical requests race and the unique constraint
    // rejects one, save() has already rolled back its own transaction, so we can safely re-read
    // the winner's row and answer idempotently.
    @Override
    public FollowResponse followUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("You cannot follow yourself");
        }

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Follow existing = followRepository.findByFollowerIdAndFollowingId(userId, targetUserId).orElse(null);
        if (existing != null) {
            return dtoMapper.toFollowResponse(existing);
        }

        Follow follow = Follow.builder()
                .follower(follower)
                .following(target)
                .build();

        boolean created = true;
        try {
            follow = followRepository.save(follow);
        } catch (DataIntegrityViolationException e) {
            follow = followRepository.findByFollowerIdAndFollowingId(userId, targetUserId).orElseThrow(() -> e);
            created = false;   // a simultaneous request won; it publishes the event
        }
        if (created) {
            eventPublisher.publishEvent(new UserFollowedEvent(userId, targetUserId));
        }
        return dtoMapper.toFollowResponse(follow);
    }

    @Override
    public void unfollowUser(Long userId, Long targetUserId) {
        ensureUserExists(targetUserId);

        followRepository.findByFollowerIdAndFollowingId(userId, targetUserId)
                .ifPresent(followRepository::delete);
    }

    @Override
    public List<UserResponse> getFollowers(Long targetUserId, int page, int size) {
        ensureUserExists(targetUserId);
        return followRepository.findByFollowingIdOrderByCreatedAtDescIdDesc(
                        targetUserId, PaginationUtil.toPageable(page, size)).stream()
                .map(follow -> dtoMapper.toPublicUserResponse(follow.getFollower()))
                .toList();
    }

    @Override
    public List<UserResponse> getFollowing(Long targetUserId, int page, int size) {
        ensureUserExists(targetUserId);
        return followRepository.findByFollowerIdOrderByCreatedAtDescIdDesc(
                        targetUserId, PaginationUtil.toPageable(page, size)).stream()
                .map(follow -> dtoMapper.toPublicUserResponse(follow.getFollowing()))
                .toList();
    }

    @Override
    public FollowStatusResponse getFollowStatus(Long viewerId, Long targetUserId) {
        ensureUserExists(targetUserId);
        return FollowStatusResponse.builder()
                .userId(targetUserId)
                .followedByMe(followRepository.existsByFollowerIdAndFollowingId(viewerId, targetUserId))
                .followingMe(followRepository.existsByFollowerIdAndFollowingId(targetUserId, viewerId))
                .followersCount(followRepository.countByFollowingId(targetUserId))
                .followingCount(followRepository.countByFollowerId(targetUserId))
                .build();
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }
}
