package com.sashank.paraspa.event;

/** Published when someone asks to follow a private account (a PENDING follow was created). */
public record FollowRequestedEvent(Long followerId, Long ownerId) {
}
