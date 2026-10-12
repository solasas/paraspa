package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateRescheduleRequest;
import com.sashank.paraspa.dto.response.RescheduleRequestResponse;
import com.sashank.paraspa.dto.response.SessionResponse;
import java.util.List;

public interface RescheduleService {
    RescheduleRequestResponse proposeReschedule(Long userId, Long sessionId, CreateRescheduleRequest request);
    List<RescheduleRequestResponse> getRescheduleRequests(Long userId, Long sessionId);
    SessionResponse acceptReschedule(Long userId, Long sessionId, Long requestId);
    RescheduleRequestResponse rejectReschedule(Long userId, Long sessionId, Long requestId);
}
