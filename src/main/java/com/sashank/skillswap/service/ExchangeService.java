package com.sashank.skillswap.service;

import com.sashank.skillswap.dto.request.CreateExchangeRequest;
import com.sashank.skillswap.dto.response.ExchangeResponse;
import java.util.List;

public interface ExchangeService {
    ExchangeResponse createExchange(Long userId, CreateExchangeRequest request);
    List<ExchangeResponse> getMyExchanges(Long userId);
    List<ExchangeResponse> getSentExchanges(Long userId);
    List<ExchangeResponse> getReceivedExchanges(Long userId);
    ExchangeResponse getExchangeById(Long userId, Long exchangeId);
    ExchangeResponse acceptExchange(Long userId, Long exchangeId);
    ExchangeResponse rejectExchange(Long userId, Long exchangeId);
    ExchangeResponse completeExchange(Long userId, Long exchangeId);
}

