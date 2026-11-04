package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.NotificationRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.BlockService;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BlockServiceImpl implements BlockService {

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    @Transactional
    public void block(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new BadRequestException("You cannot block yourself");
        }
        if (!userRepository.existsById(targetId)) {
            throw new ResourceNotFoundException("User not found");
        }

        // One atomic INSERT ... ON CONFLICT DO NOTHING: a double tap cannot fail with a duplicate-key error.
        blockRepository.insertIfAbsent(userId, targetId, LocalDateTime.now());

        // Always run, even if the block already existed: the call is idempotent, so a retry also finishes any cleanup
        // that did not complete the first time.
        followRepository.deleteBetween(userId, targetId);
        notificationRepository.deleteBetween(userId, targetId);
    }

    @Override
    @Transactional
    public void unblock(Long userId, Long targetId) {
        if (!userRepository.existsById(targetId)) {
            throw new ResourceNotFoundException("User not found");
        }
        blockRepository.deleteBlock(userId, targetId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getBlockedUsers(Long userId, int page, int size) {
        return blockRepository.findByBlockerIdOrderByIdDesc(userId, PaginationUtil.toPageable(page, size)).stream()
                .map(block -> dtoMapper.toPublicUserResponse(block.getBlocked()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getHiddenUserIds(Long viewerId) {
        return new HashSet<>(blockRepository.findHiddenUserIds(viewerId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(Long a, Long b) {
        return !a.equals(b) && blockRepository.existsBetween(a, b);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireNoBlock(Long actorId, Long otherUserId) {
        if (actorId.equals(otherUserId)) {
            return;
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(actorId, otherUserId)) {
            throw new BadRequestException("You have blocked this user. Unblock them first.");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(otherUserId, actorId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }
}
