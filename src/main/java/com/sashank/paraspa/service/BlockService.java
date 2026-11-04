package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.UserResponse;
import java.util.List;
import java.util.Set;

public interface BlockService {

    /**
     * Blocks a user. Idempotent. In one transaction it also removes the follows (and follow requests) between the two
     * in both directions and the notifications they caused for each other. You cannot block yourself (400).
     */
    void block(Long userId, Long targetId);

    /** Removes a block. Idempotent. Follows removed by the block are not restored. */
    void unblock(Long userId, Long targetId);

    /** The people this user has blocked (not the people who blocked them), newest block first. */
    List<UserResponse> getBlockedUsers(Long userId, int page, int size);

    /**
     * The users hidden from {@code viewerId}: everyone blocked by them and everyone who blocked them. Both directions
     * are hidden everywhere, so content never reaches someone through the other person's side of the block.
     */
    Set<Long> getHiddenUserIds(Long viewerId);

    boolean isBlockedEitherWay(Long a, Long b);

    /**
     * Refuses an interaction (follow, message, exchange...) between two users when one has blocked the other.
     * If the actor blocked the other person: 400 telling them to unblock first. If the other person blocked the actor:
     * 404 "User not found", so a blocked person cannot tell a block from a missing account.
     */
    void requireNoBlock(Long actorId, Long otherUserId);
}
