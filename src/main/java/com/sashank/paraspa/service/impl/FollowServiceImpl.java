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
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.AccessPolicy;
import com.sashank.paraspa.service.BlockService;
import com.sashank.paraspa.service.FollowService;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockService blockService;

    @Autowired
    private AccessPolicy accessPolicy;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private DtoMapper dtoMapper;

    // Deliberately not @Transactional: if two identical requests race and the unique constraint rejects one, save() has
    // already rolled back its own transaction, so we can safely re-read the winner's row and answer idempotently.
    @Override
    public FollowResponse followUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BadRequestException("You cannot follow yourself");
        }

        User follower = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        blockService.requireNoBlock(userId, targetUserId);

        Follow existing = followRepository.findByFollowerIdAndFollowingId(userId, targetUserId).orElse(null);
        if (existing != null) {
            return dtoMapper.toFollowResponse(existing);   // already following, or already asked: nothing changes
        }

        // a private account turns the follow into a request the owner must approve
        FollowStatus status = target.isPrivate() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        Follow follow = Follow.builder()
                .follower(follower)
                .following(target)
                .status(status)
                .build();

        boolean created = true;
        try {
            follow = followRepository.save(follow);
        } catch (DataIntegrityViolationException e) {
            follow = followRepository.findByFollowerIdAndFollowingId(userId, targetUserId).orElseThrow(() -> e);
            created = false;   // a simultaneous request won; it publishes the event
        }
        if (created) {
            eventPublisher.publishEvent(status == FollowStatus.PENDING
                    ? new FollowRequestedEvent(userId, targetUserId)
                    : new UserFollowedEvent(userId, targetUserId));
        }
        return dtoMapper.toFollowResponse(follow);
    }

    @Override
    @Transactional
    public void unfollowUser(Long userId, Long targetUserId) {
        ensureUserExists(targetUserId);

        followRepository.findByFollowerIdAndFollowingId(userId, targetUserId)
                .ifPresent(followRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getFollowers(Long viewerId, Long targetUserId, int page, int size) {
        User target = requireListable(viewerId, targetUserId);
        return followRepository.findAcceptedFollowers(target.getId(), viewerId, PaginationUtil.toPageable(page, size)).stream()
                .map(follow -> dtoMapper.toPublicUserResponse(follow.getFollower()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getFollowing(Long viewerId, Long targetUserId, int page, int size) {
        User target = requireListable(viewerId, targetUserId);
        return followRepository.findAcceptedFollowing(target.getId(), viewerId, PaginationUtil.toPageable(page, size)).stream()
                .map(follow -> dtoMapper.toPublicUserResponse(follow.getFollowing()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatusResponse getFollowStatus(Long viewerId, Long targetUserId) {
        ensureUserExists(targetUserId);
        accessPolicy.requireUserVisible(viewerId, targetUserId);

        Follow mine = followRepository.findByFollowerIdAndFollowingId(viewerId, targetUserId).orElse(null);
        return FollowStatusResponse.builder()
                .userId(targetUserId)
                .followedByMe(mine != null && mine.getStatus() == FollowStatus.ACCEPTED)
                .followRequestedByMe(mine != null && mine.getStatus() == FollowStatus.PENDING)
                .followingMe(followRepository.existsByFollowerIdAndFollowingIdAndStatus(targetUserId, viewerId, FollowStatus.ACCEPTED))
                .followersCount(followRepository.countByFollowingIdAndStatus(targetUserId, FollowStatus.ACCEPTED))
                .followingCount(followRepository.countByFollowerIdAndStatus(targetUserId, FollowStatus.ACCEPTED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowRequestResponse> getFollowRequests(Long ownerId, int page, int size) {
        return followRepository.findByFollowingIdAndStatusOrderByCreatedAtDescIdDesc(
                        ownerId, FollowStatus.PENDING, PaginationUtil.toPageable(page, size)).stream()
                .map(follow -> FollowRequestResponse.builder()
                        .id(follow.getId())
                        .requester(dtoMapper.toPublicUserResponse(follow.getFollower()))
                        .createdAt(follow.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public void acceptFollowRequest(Long ownerId, Long followId) {
        Follow request = findPendingRequest(ownerId, followId);
        request.setStatus(FollowStatus.ACCEPTED);
        followRepository.save(request);
        eventPublisher.publishEvent(new FollowRequestAcceptedEvent(request.getFollower().getId(), ownerId));
    }

    @Override
    @Transactional
    public void rejectFollowRequest(Long ownerId, Long followId) {
        followRepository.delete(findPendingRequest(ownerId, followId));
    }

    private Follow findPendingRequest(Long ownerId, Long followId) {
        return followRepository.findByIdAndFollowingIdAndStatus(followId, ownerId, FollowStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Follow request not found"));
    }

    /** The user whose follower lists are asked for: must exist, not be blocked with the viewer, and not be private to them. */
    private User requireListable(Long viewerId, Long targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        accessPolicy.requireCanSeePostsOf(viewerId, target);
        return target;
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }
}
