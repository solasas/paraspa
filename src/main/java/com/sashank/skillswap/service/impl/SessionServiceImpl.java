package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.request.CreateSessionRequest;
import com.sashank.skillswap.dto.response.SessionResponse;
import com.sashank.skillswap.entity.Session;
import com.sashank.skillswap.entity.SkillExchange;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.enums.ExchangeStatus;
import com.sashank.skillswap.enums.SessionStatus;
import com.sashank.skillswap.exception.BadRequestException;
import com.sashank.skillswap.exception.ResourceNotFoundException;
import com.sashank.skillswap.repository.SessionRepository;
import com.sashank.skillswap.repository.SkillExchangeRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.service.SessionService;
import com.sashank.skillswap.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SessionServiceImpl implements SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SkillExchangeRepository exchangeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public SessionResponse createSession(Long userId, Long exchangeId, CreateSessionRequest request) {
        User scheduledBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SkillExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange not found"));

        if (!exchange.getRequester().getId().equals(userId) && !exchange.getReceiver().getId().equals(userId)) {
            throw new BadRequestException("Only exchange participants can schedule sessions");
        }

        if (!exchange.getStatus().equals(ExchangeStatus.ACCEPTED)) {
            throw new BadRequestException("Exchange must be accepted before scheduling sessions");
        }

        Session session = Session.builder()
                .exchange(exchange)
                .scheduledBy(scheduledBy)
                .dateTime(request.getDateTime())
                .durationMinutes(request.getDurationMinutes())
                .mode(request.getMode())
                .meetLink(request.getMeetLink())
                .location(request.getLocation())
                .notes(request.getNotes())
                .status(SessionStatus.SCHEDULED)
                .build();

        session = sessionRepository.save(session);
        return dtoMapper.toSessionResponse(session);
    }

    @Override
    public List<SessionResponse> getSessionsByExchange(Long userId, Long exchangeId) {
        SkillExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange not found"));

        if (!exchange.getRequester().getId().equals(userId) && !exchange.getReceiver().getId().equals(userId)) {
            throw new ResourceNotFoundException("Exchange not found");
        }

        return sessionRepository.findByExchangeId(exchangeId).stream()
                .map(dtoMapper::toSessionResponse)
                .toList();
    }

    @Override
    public List<SessionResponse> getUpcomingSessions(Long userId) {
        return sessionRepository.findUpcomingSessionsByUserId(userId).stream()
                .map(dtoMapper::toSessionResponse)
                .toList();
    }

    @Override
    public SessionResponse getSessionById(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getExchange().getRequester().getId().equals(userId) &&
                !session.getExchange().getReceiver().getId().equals(userId)) {
            throw new ResourceNotFoundException("Session not found");
        }

        return dtoMapper.toSessionResponse(session);
    }

    @Override
    public SessionResponse completeSession(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getExchange().getRequester().getId().equals(userId) &&
                !session.getExchange().getReceiver().getId().equals(userId)) {
            throw new BadRequestException("Only exchange participants can complete session");
        }

        if (!session.getStatus().equals(SessionStatus.SCHEDULED)) {
            throw new BadRequestException("Only scheduled sessions can be completed");
        }

        session.setStatus(SessionStatus.COMPLETED);
        session = sessionRepository.save(session);
        return dtoMapper.toSessionResponse(session);
    }

    @Override
    public SessionResponse cancelSession(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getScheduledBy().getId().equals(userId)) {
            throw new BadRequestException("Only session scheduler can cancel session");
        }

        if (!session.getStatus().equals(SessionStatus.SCHEDULED)) {
            throw new BadRequestException("Only scheduled sessions can be cancelled");
        }

        session.setStatus(SessionStatus.CANCELLED);
        session = sessionRepository.save(session);
        return dtoMapper.toSessionResponse(session);
    }
}

