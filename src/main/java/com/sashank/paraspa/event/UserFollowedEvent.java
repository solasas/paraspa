package com.sashank.paraspa.event;

/** Published when a follow relationship is created (not when an existing one is repeated). */
public record UserFollowedEvent(Long followerId, Long followedId) {
}
