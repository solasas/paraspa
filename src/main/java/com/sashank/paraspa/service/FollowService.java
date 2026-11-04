package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.FollowRequestResponse;
import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import java.util.List;

public interface FollowService {

    /**
     * Idempotent: following someone you already follow, or already asked to follow, returns the existing follow.
     * Following a private account creates a PENDING request the owner must approve; a public account is followed at
     * once. Refused when either side has blocked the other.
     */
    FollowResponse followUser(Long userId, Long targetUserId);

    /** Idempotent. Also cancels a pending request. */
    void unfollowUser(Long userId, Long targetUserId);

    /** Accepted followers, newest first. The followers of a private account are visible to its accepted followers only. */
    List<UserResponse> getFollowers(Long viewerId, Long targetUserId, int page, int size);

    List<UserResponse> getFollowing(Long viewerId, Long targetUserId, int page, int size);

    FollowStatusResponse getFollowStatus(Long viewerId, Long targetUserId);

    /** The requests to follow my private account that are waiting for me, newest first. */
    List<FollowRequestResponse> getFollowRequests(Long ownerId, int page, int size);

    /** Accepts a request addressed to me. Another person's or an unknown request: 404. */
    void acceptFollowRequest(Long ownerId, Long followId);

    /** Rejects (deletes) a request addressed to me. The requester is not told. */
    void rejectFollowRequest(Long ownerId, Long followId);
}
