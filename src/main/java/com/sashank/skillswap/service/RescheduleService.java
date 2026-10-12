package com.sashank.skillswap.service;

import com.sashank.skillswap.dto.request.CreateRescheduleRequest;
import com.sashank.skillswap.dto.response.RescheduleRequestResponse;
import com.sashank.skillswap.dto.response.SessionResponse;
import java.util.List;

public interface RescheduleService {
    RescheduleRequestResponse proposeReschedule(Long userId, Long sessionId, CreateRescheduleRequest request);
    List<RescheduleRequestResponse> getRescheduleRequests(Long userId, Long sessionId);
    SessionResponse acceptReschedule(Long userId, Long sessionId, Long requestId);
    RescheduleRequestResponse rejectReschedule(Long userId, Long sessionId, Long requestId);
}
