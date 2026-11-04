package com.sashank.paraspa.event;

public record ExchangeRequestedEvent(Long exchangeId, Long requesterId, Long receiverId) {
}
