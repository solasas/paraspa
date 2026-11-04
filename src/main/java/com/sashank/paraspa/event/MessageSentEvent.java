package com.sashank.paraspa.event;

public record MessageSentEvent(Long messageId, Long senderId, Long receiverId) {
}
