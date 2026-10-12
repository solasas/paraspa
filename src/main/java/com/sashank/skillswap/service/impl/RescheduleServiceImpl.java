package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.request.CreateRescheduleRequest;
import com.sashank.skillswap.dto.response.RescheduleRequestResponse;
import com.sashank.skillswap.dto.response.SessionResponse;
import com.sashank.skillswap.entity.RescheduleRequest;
import com.sashank.skillswap.entity.Session;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.enums.RescheduleStatus;
import com.sashank.skillswap.enums.SessionStatus;
import com.sashank.skillswap.exception.BadRequestException;
import com.sashank.skillswap.exception.ResourceNotFoundException;
import com.sashank.skillswap.repository.RescheduleRequestRepository;
import com.sashank.skillswap.repository.SessionRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.service.RescheduleService;
import com.sashank.skillswap.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RescheduleServiceImpl implements RescheduleService {

    @Autowired
    private RescheduleRequestRepository rescheduleRequestRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public RescheduleRequestResponse proposeReschedule(Long userId, Long sessionId, CreateRescheduleRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        User requestedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!isParticipant(session, userId)) {
            throw new BadRequestException("Only exchange participants can propose a reschedule");
        }

        if (!session.getStatus().equals(SessionStatus.SCHEDULED)) {
            throw new BadRequestException("Only scheduled sessions can be rescheduled");
        }

        if (rescheduleRequestRepository.existsBySessionIdAndStatus(sessionId, RescheduleStatus.PENDING)) {
            throw new BadRequestException("A reschedule request is already pending for this session");
        }

        RescheduleRequest rescheduleRequest = RescheduleRequest.builder()
                .session(session)
                .requestedBy(requestedBy)
                .proposedDateTime(request.getProposedDateTime())
                .reason(request.getReason())
                .status(RescheduleStatus.PENDING)
                .build();

        rescheduleRequest = rescheduleRequestRepository.save(rescheduleRequest);
        return dtoMapper.toRescheduleRequestResponse(rescheduleRequest);
    }

    @Override
    public List<RescheduleRequestResponse> getRescheduleRequests(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!isParticipant(session, userId)) {
            throw new ResourceNotFoundException("Session not found");
        }

        return rescheduleRequestRepository.findBySessionId(sessionId).stream()
                .map(dtoMapper::toRescheduleRequestResponse)
                .toList();
    }

    @Override
    public SessionResponse acceptReschedule(Long userId, Long sessionId, Long requestId) {
        RescheduleRequest rescheduleRequest = getPendingRequestForResponse(userId, sessionId, requestId);

        Session session = rescheduleRequest.getSession();
        session.setDateTime(rescheduleRequest.getProposedDateTime());
        session = sessionRepository.save(session);

        rescheduleRequest.setStatus(RescheduleStatus.ACCEPTED);
        rescheduleRequestRepository.save(rescheduleRequest);

        return dtoMapper.toSessionResponse(session);
    }

    @Override
    public RescheduleRequestResponse rejectReschedule(Long userId, Long sessionId, Long requestId) {
        RescheduleRequest rescheduleRequest = getPendingRequestForResponse(userId, sessionId, requestId);

        rescheduleRequest.setStatus(RescheduleStatus.REJECTED);
        rescheduleRequest = rescheduleRequestRepository.save(rescheduleRequest);

        return dtoMapper.toRescheduleRequestResponse(rescheduleRequest);
    }

    private RescheduleRequest getPendingRequestForResponse(Long userId, Long sessionId, Long requestId) {
        RescheduleRequest rescheduleRequest = rescheduleRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Reschedule request not found"));

        if (!rescheduleRequest.getSession().getId().equals(sessionId)) {
            throw new ResourceNotFoundException("Reschedule request not found");
        }

        if (!isParticipant(rescheduleRequest.getSession(), userId)) {
            throw new BadRequestException("Only exchange participants can respond to reschedule requests");
        }

        if (rescheduleRequest.getRequestedBy().getId().equals(userId)) {
            throw new BadRequestException("Cannot respond to your own reschedule request");
        }

        if (!rescheduleRequest.getStatus().equals(RescheduleStatus.PENDING)) {
            throw new BadRequestException("Reschedule request is not pending");
        }

        return rescheduleRequest;
    }

    private boolean isParticipant(Session session, Long userId) {
        return session.getExchange().getRequester().getId().equals(userId)
                || session.getExchange().getReceiver().getId().equals(userId);
    }
}
