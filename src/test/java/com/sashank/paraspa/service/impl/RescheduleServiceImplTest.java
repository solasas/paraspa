package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateRescheduleRequest;
import com.sashank.paraspa.dto.response.RescheduleRequestResponse;
import com.sashank.paraspa.dto.response.SessionResponse;
import com.sashank.paraspa.entity.RescheduleRequest;
import com.sashank.paraspa.entity.Session;
import com.sashank.paraspa.entity.SkillExchange;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.RescheduleStatus;
import com.sashank.paraspa.enums.SessionStatus;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.RescheduleRequestRepository;
import com.sashank.paraspa.repository.SessionRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RescheduleServiceImplTest {

    @Mock
    private RescheduleRequestRepository rescheduleRequestRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private RescheduleServiceImpl rescheduleService;

    private Session buildSession(Long sessionId, User requester, User receiver, SessionStatus status) {
        SkillExchange exchange = SkillExchange.builder().requester(requester).receiver(receiver).build();
        return Session.builder().id(sessionId).exchange(exchange).status(status).build();
    }

    @Test
    void proposeReschedule_shouldThrowBadRequestException_whenUserNotParticipant() {
        Long sessionId = 100L;
        Long outsiderId = 99L;
        User requester = User.builder().id(1L).build();
        User receiver = User.builder().id(2L).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.SCHEDULED);
        User outsider = User.builder().id(outsiderId).build();

        CreateRescheduleRequest request = CreateRescheduleRequest.builder()
                .proposedDateTime(LocalDateTime.now().plusDays(1))
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(outsiderId)).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> rescheduleService.proposeReschedule(outsiderId, sessionId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only exchange participants can propose a reschedule");

        verify(rescheduleRequestRepository, never()).save(any());
    }

    @Test
    void proposeReschedule_shouldThrowBadRequestException_whenSessionNotScheduled() {
        Long sessionId = 100L;
        Long userId = 1L;
        User requester = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.COMPLETED);

        CreateRescheduleRequest request = CreateRescheduleRequest.builder()
                .proposedDateTime(LocalDateTime.now().plusDays(1))
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> rescheduleService.proposeReschedule(userId, sessionId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only scheduled sessions can be rescheduled");

        verify(rescheduleRequestRepository, never()).save(any());
    }

    @Test
    void proposeReschedule_shouldThrowBadRequestException_whenPendingRequestAlreadyExists() {
        Long sessionId = 100L;
        Long userId = 1L;
        User requester = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.SCHEDULED);

        CreateRescheduleRequest request = CreateRescheduleRequest.builder()
                .proposedDateTime(LocalDateTime.now().plusDays(1))
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(rescheduleRequestRepository.existsBySessionIdAndStatus(sessionId, RescheduleStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> rescheduleService.proposeReschedule(userId, sessionId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A reschedule request is already pending for this session");

        verify(rescheduleRequestRepository, never()).save(any());
    }

    @Test
    void proposeReschedule_shouldSaveRequestWithPendingStatus_whenValid() {
        Long sessionId = 100L;
        Long userId = 1L;
        User requester = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.SCHEDULED);
        LocalDateTime proposedTime = LocalDateTime.now().plusDays(2);

        CreateRescheduleRequest request = CreateRescheduleRequest.builder()
                .proposedDateTime(proposedTime)
                .reason("Conflict came up")
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(userId)).thenReturn(Optional.of(requester));
        when(rescheduleRequestRepository.existsBySessionIdAndStatus(sessionId, RescheduleStatus.PENDING)).thenReturn(false);
        when(rescheduleRequestRepository.save(any(RescheduleRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toRescheduleRequestResponse(any(RescheduleRequest.class))).thenReturn(RescheduleRequestResponse.builder().build());

        rescheduleService.proposeReschedule(userId, sessionId, request);

        ArgumentCaptor<RescheduleRequest> captor = ArgumentCaptor.forClass(RescheduleRequest.class);
        verify(rescheduleRequestRepository).save(captor.capture());

        RescheduleRequest saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(RescheduleStatus.PENDING);
        assertThat(saved.getProposedDateTime()).isEqualTo(proposedTime);
        assertThat(saved.getRequestedBy()).isEqualTo(requester);
    }

    @Test
    void acceptReschedule_shouldThrowBadRequestException_whenAccepterIsTheRequester() {
        Long sessionId = 100L;
        Long requestId = 200L;
        Long userId = 1L;
        User requester = User.builder().id(userId).build();
        User receiver = User.builder().id(2L).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.SCHEDULED);

        RescheduleRequest rescheduleRequest = RescheduleRequest.builder()
                .id(requestId)
                .session(session)
                .requestedBy(requester)
                .status(RescheduleStatus.PENDING)
                .proposedDateTime(LocalDateTime.now().plusDays(1))
                .build();

        when(rescheduleRequestRepository.findById(requestId)).thenReturn(Optional.of(rescheduleRequest));

        assertThatThrownBy(() -> rescheduleService.acceptReschedule(userId, sessionId, requestId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot respond to your own reschedule request");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void acceptReschedule_shouldUpdateSessionDateTimeAndMarkAccepted_whenValid() {
        Long sessionId = 100L;
        Long requestId = 200L;
        Long requesterId = 1L;
        Long accepterId = 2L;
        User requester = User.builder().id(requesterId).build();
        User receiver = User.builder().id(accepterId).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.SCHEDULED);
        LocalDateTime proposedTime = LocalDateTime.now().plusDays(3);

        RescheduleRequest rescheduleRequest = RescheduleRequest.builder()
                .id(requestId)
                .session(session)
                .requestedBy(requester)
                .status(RescheduleStatus.PENDING)
                .proposedDateTime(proposedTime)
                .build();

        when(rescheduleRequestRepository.findById(requestId)).thenReturn(Optional.of(rescheduleRequest));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rescheduleRequestRepository.save(any(RescheduleRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toSessionResponse(any(Session.class))).thenReturn(SessionResponse.builder().build());

        rescheduleService.acceptReschedule(accepterId, sessionId, requestId);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getDateTime()).isEqualTo(proposedTime);

        ArgumentCaptor<RescheduleRequest> requestCaptor = ArgumentCaptor.forClass(RescheduleRequest.class);
        verify(rescheduleRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(RescheduleStatus.ACCEPTED);
    }

    @Test
    void rejectReschedule_shouldMarkRequestRejected_whenValid() {
        Long sessionId = 100L;
        Long requestId = 200L;
        Long requesterId = 1L;
        Long rejecterId = 2L;
        User requester = User.builder().id(requesterId).build();
        User receiver = User.builder().id(rejecterId).build();
        Session session = buildSession(sessionId, requester, receiver, SessionStatus.SCHEDULED);

        RescheduleRequest rescheduleRequest = RescheduleRequest.builder()
                .id(requestId)
                .session(session)
                .requestedBy(requester)
                .status(RescheduleStatus.PENDING)
                .proposedDateTime(LocalDateTime.now().plusDays(1))
                .build();

        when(rescheduleRequestRepository.findById(requestId)).thenReturn(Optional.of(rescheduleRequest));
        when(rescheduleRequestRepository.save(any(RescheduleRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toRescheduleRequestResponse(any(RescheduleRequest.class))).thenReturn(RescheduleRequestResponse.builder().build());

        rescheduleService.rejectReschedule(rejecterId, sessionId, requestId);

        ArgumentCaptor<RescheduleRequest> captor = ArgumentCaptor.forClass(RescheduleRequest.class);
        verify(rescheduleRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RescheduleStatus.REJECTED);
    }

    @Test
    void getRescheduleRequests_shouldThrowNotFound_whenUserIsNotParticipant() {
        Session session = buildSession(10L, User.builder().id(1L).build(), User.builder().id(2L).build(), SessionStatus.SCHEDULED);

        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> rescheduleService.getRescheduleRequests(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Session not found");

        verify(rescheduleRequestRepository, never()).findBySessionId(any());
    }
}
