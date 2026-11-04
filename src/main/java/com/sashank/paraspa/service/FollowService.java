package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatsResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import java.util.List;

public interface FollowService {
    FollowResponse followUser(Long userId, Long targetUserId);
    void unfollowUser(Long userId, Long targetUserId);
    List<UserResponse> getFollowers(Long targetUserId);
    List<UserResponse> getFollowing(Long targetUserId);
    FollowStatsResponse getFollowStats(Long viewerId, Long targetUserId);
}
