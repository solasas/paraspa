package com.sashank.paraspa.enums;

public enum NotificationType {
    FOLLOW,
    LIKE,
    COMMENT,
    REPLY,
    EXCHANGE_REQUEST,
    EXCHANGE_ACCEPTED,
    MESSAGE,
    /** Someone asked to follow my private account. */
    FOLLOW_REQUEST,
    /** The owner of a private account accepted my follow request. */
    FOLLOW_ACCEPTED
}
