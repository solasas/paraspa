package com.sashank.paraspa.event;

/** @param accepterId the receiver who accepted; {@code requesterId} is the person who is told */
public record ExchangeAcceptedEvent(Long exchangeId, Long requesterId, Long accepterId) {
}
