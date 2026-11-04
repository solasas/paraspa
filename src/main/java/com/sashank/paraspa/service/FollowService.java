package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import java.util.List;

public interface FollowService {
    /** Idempotent: following someone you already follow returns the existing follow. */
    FollowResponse followUser(Long userId, Long targetUserId);

    /** Idempotent: unfollowing someone you do not follow does nothing. */
    void unfollowUser(Long userId, Long targetUserId);

    List<UserResponse> getFollowers(Long targetUserId, int page, int size);
    List<UserResponse> getFollowing(Long targetUserId, int page, int size);
    FollowStatusResponse getFollowStatus(Long viewerId, Long targetUserId);
}
