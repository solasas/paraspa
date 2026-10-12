package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.request.CreateSessionRequest;
import com.sashank.skillswap.dto.response.SessionResponse;
import com.sashank.skillswap.entity.Session;
import com.sashank.skillswap.entity.SkillExchange;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.enums.SessionStatus;
import com.sashank.skillswap.exception.BadRequestException;
import com.sashank.skillswap.repository.SessionRepository;
import com.sashank.skillswap.repository.SkillExchangeRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.util.DtoMapper;
import com.sashank.skillswap.exception.ResourceNotFoundException;
import com.sashank.skillswap.enums.ExchangeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SkillExchangeRepository exchangeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private SessionServiceImpl sessionService;

    @Test
    void createSession_shouldThrowBadRequestException_whenUserIsNotExchangeParticipant() {
        Long exchangeId = 100L;
        Long outsiderId = 999L;

        User scheduledBy = User.builder().id(outsiderId).build();
        User requester = User.builder().id(1L).build();
        User receiver = User.builder().id(2L).build();

        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .requester(requester)
                .receiver(receiver)
                .build();

        CreateSessionRequest request = CreateSessionRequest.builder().build();

        when(userRepository.findById(outsiderId)).thenReturn(Optional.of(scheduledBy));
        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> sessionService.createSession(outsiderId, exchangeId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only exchange participants can schedule sessions");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void completeSession_shouldUpdateStatusToCompleted() {
        Long userId = 1L;
        Long sessionId = 200L;

        User requester = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        SkillExchange exchange = SkillExchange.builder()
                .requester(requester)
                .receiver(receiver)
                .build();

        Session session = Session.builder()
                .id(sessionId)
                .exchange(exchange)
                .status(SessionStatus.SCHEDULED)
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toSessionResponse(any(Session.class))).thenReturn(SessionResponse.builder().build());

        sessionService.completeSession(userId, sessionId);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(sessionCaptor.capture());

        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void cancelSession_shouldUpdateStatusToCancelled() {
        Long userId = 1L;
        Long sessionId = 200L;

        User scheduledBy = User.builder().id(userId).build();
        Session session = Session.builder()
                .id(sessionId)
                .scheduledBy(scheduledBy)
                .status(SessionStatus.SCHEDULED)
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toSessionResponse(any(Session.class))).thenReturn(SessionResponse.builder().build());

        sessionService.cancelSession(userId, sessionId);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(sessionCaptor.capture());

        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(SessionStatus.CANCELLED);
    }

    private SkillExchange exchangeOf(Long requesterId, Long receiverId) {
        return SkillExchange.builder()
                .id(100L)
                .requester(User.builder().id(requesterId).build())
                .receiver(User.builder().id(receiverId).build())
                .status(ExchangeStatus.ACCEPTED)
                .build();
    }

    @Test
    void getSessionById_shouldThrowNotFound_whenUserIsNotParticipant() {
        Session session = Session.builder()
                .id(200L)
                .exchange(exchangeOf(1L, 2L))
                .status(SessionStatus.SCHEDULED)
                .build();

        when(sessionRepository.findById(200L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.getSessionById(999L, 200L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Session not found");
    }

    @Test
    void getSessionsByExchange_shouldThrowNotFound_whenUserIsNotParticipant() {
        when(exchangeRepository.findById(100L)).thenReturn(Optional.of(exchangeOf(1L, 2L)));

        assertThatThrownBy(() -> sessionService.getSessionsByExchange(999L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Exchange not found");

        verify(sessionRepository, never()).findByExchangeId(any());
    }

    @Test
    void completeSession_shouldThrowBadRequestException_whenSessionIsNotScheduled() {
        Session session = Session.builder()
                .id(200L)
                .exchange(exchangeOf(1L, 2L))
                .status(SessionStatus.CANCELLED)
                .build();

        when(sessionRepository.findById(200L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.completeSession(1L, 200L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only scheduled sessions can be completed");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelSession_shouldThrowBadRequestException_whenSessionIsNotScheduled() {
        Session session = Session.builder()
                .id(200L)
                .scheduledBy(User.builder().id(1L).build())
                .status(SessionStatus.COMPLETED)
                .build();

        when(sessionRepository.findById(200L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.cancelSession(1L, 200L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only scheduled sessions can be cancelled");

        verify(sessionRepository, never()).save(any());
    }
}
