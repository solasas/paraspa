package com.sashank.paraspa.event;

/** Published when a like is really added (not when an existing like is repeated). */
public record PostLikedEvent(Long postId, Long postAuthorId, Long likerId) {
}
