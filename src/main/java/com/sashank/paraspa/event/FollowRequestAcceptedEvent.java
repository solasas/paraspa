package com.sashank.paraspa.event;

/** Published when the owner of a private account accepts a follow request. */
public record FollowRequestAcceptedEvent(Long followerId, Long ownerId) {
}
