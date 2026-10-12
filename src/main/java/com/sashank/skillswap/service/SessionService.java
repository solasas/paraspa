package com.sashank.skillswap.service;

import com.sashank.skillswap.dto.request.CreateSessionRequest;
import com.sashank.skillswap.dto.response.SessionResponse;
import java.util.List;

public interface SessionService {
    SessionResponse createSession(Long userId, Long exchangeId, CreateSessionRequest request);
    List<SessionResponse> getSessionsByExchange(Long userId, Long exchangeId);
    List<SessionResponse> getUpcomingSessions(Long userId);
    SessionResponse getSessionById(Long userId, Long sessionId);
    SessionResponse completeSession(Long userId, Long sessionId);
    SessionResponse cancelSession(Long userId, Long sessionId);
}

