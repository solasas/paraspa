package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateExchangeRequest;
import com.sashank.paraspa.dto.response.ExchangeResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.SkillExchange;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.ExchangeStatus;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.repository.SkillExchangeRepository;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceImplTest {

    @Mock
    private SkillExchangeRepository exchangeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private DtoMapper dtoMapper;

    @Mock
    private com.sashank.paraspa.service.BlockService blockService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExchangeServiceImpl exchangeService;

    @Test
    void createExchange_shouldThrowBadRequestException_whenRequesterEqualsReceiver() {
        Long userId = 1L;
        User user = User.builder().id(userId).build();

        CreateExchangeRequest request = CreateExchangeRequest.builder()
                .receiverId(userId)
                .offeredSkillId(10L)
                .wantedSkillId(20L)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> exchangeService.createExchange(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot send exchange request to yourself");

        verify(skillRepository, never()).findById(anyLong());
        verify(exchangeRepository, never()).save(any());
    }

    @Test
    void createExchange_shouldSaveExchangeWithPendingStatus() {
        Long requesterId = 1L;
        Long receiverId = 2L;

        User requester = User.builder().id(requesterId).build();
        User receiver = User.builder().id(receiverId).build();
        Skill offeredSkill = Skill.builder().id(10L).build();
        Skill wantedSkill = Skill.builder().id(20L).build();

        CreateExchangeRequest request = CreateExchangeRequest.builder()
                .receiverId(receiverId)
                .offeredSkillId(10L)
                .wantedSkillId(20L)
                .message("Let's swap skills")
                .build();

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(skillRepository.findById(10L)).thenReturn(Optional.of(offeredSkill));
        when(skillRepository.findById(20L)).thenReturn(Optional.of(wantedSkill));
        when(userSkillRepository.existsByUserIdAndSkillIdAndType(requesterId, 10L, SkillType.TEACH)).thenReturn(true);
        when(userSkillRepository.existsByUserIdAndSkillIdAndType(receiverId, 20L, SkillType.TEACH)).thenReturn(true);
        when(exchangeRepository.save(any(SkillExchange.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toExchangeResponse(any(SkillExchange.class))).thenReturn(ExchangeResponse.builder().build());

        exchangeService.createExchange(requesterId, request);

        ArgumentCaptor<SkillExchange> exchangeCaptor = ArgumentCaptor.forClass(SkillExchange.class);
        verify(exchangeRepository).save(exchangeCaptor.capture());

        SkillExchange savedExchange = exchangeCaptor.getValue();
        assertThat(savedExchange.getStatus()).isEqualTo(ExchangeStatus.PENDING);
        assertThat(savedExchange.getRequester()).isEqualTo(requester);
        assertThat(savedExchange.getReceiver()).isEqualTo(receiver);
    }

    @Test
    void acceptExchange_shouldThrowBadRequestException_whenStatusIsNotPending() {
        Long userId = 2L;
        Long exchangeId = 100L;

        User receiver = User.builder().id(userId).build();
        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .receiver(receiver)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.acceptExchange(userId, exchangeId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Exchange is not in pending status");

        verify(exchangeRepository, never()).save(any());
    }

    @Test
    void acceptExchange_shouldUpdateStatusToAcceptedAndSave() {
        Long userId = 2L;
        Long exchangeId = 100L;

        User receiver = User.builder().id(userId).build();
        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .requester(User.builder().id(1L).build())
                .receiver(receiver)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));
        when(exchangeRepository.save(any(SkillExchange.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toExchangeResponse(any(SkillExchange.class))).thenReturn(ExchangeResponse.builder().build());

        exchangeService.acceptExchange(userId, exchangeId);

        ArgumentCaptor<SkillExchange> exchangeCaptor = ArgumentCaptor.forClass(SkillExchange.class);
        verify(exchangeRepository).save(exchangeCaptor.capture());

        assertThat(exchangeCaptor.getValue().getStatus()).isEqualTo(ExchangeStatus.ACCEPTED);
    }

    @Test
    void rejectExchange_shouldThrowBadRequestException_whenStatusIsNotPending() {
        Long userId = 2L;
        Long exchangeId = 100L;

        User receiver = User.builder().id(userId).build();
        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .receiver(receiver)
                .status(ExchangeStatus.COMPLETED)
                .build();

        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.rejectExchange(userId, exchangeId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Exchange is not in pending status");

        verify(exchangeRepository, never()).save(any());
    }

    @Test
    void getExchangeById_shouldThrowNotFound_whenUserIsNotParticipant() {
        Long exchangeId = 100L;
        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .requester(User.builder().id(1L).build())
                .receiver(User.builder().id(2L).build())
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.getExchangeById(999L, exchangeId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Exchange not found");
    }

    @Test
    void getExchangeById_shouldReturnExchange_whenUserIsParticipant() {
        Long exchangeId = 100L;
        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .requester(User.builder().id(1L).build())
                .receiver(User.builder().id(2L).build())
                .status(ExchangeStatus.PENDING)
                .build();
        ExchangeResponse expected = ExchangeResponse.builder().id(exchangeId).build();

        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));
        when(dtoMapper.toExchangeResponse(exchange)).thenReturn(expected);

        assertThat(exchangeService.getExchangeById(2L, exchangeId)).isSameAs(expected);
    }

    @Test
    void completeExchange_shouldThrowBadRequestException_whenStatusIsNotAccepted() {
        Long userId = 1L;
        Long exchangeId = 100L;
        SkillExchange exchange = SkillExchange.builder()
                .id(exchangeId)
                .requester(User.builder().id(userId).build())
                .receiver(User.builder().id(2L).build())
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findById(exchangeId)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.completeExchange(userId, exchangeId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only accepted exchanges can be completed");

        verify(exchangeRepository, never()).save(any());
    }


    @Test
    void getMyExchanges_shouldQueryRequestedPageNewestFirst() {
        when(exchangeRepository.findByRequesterIdOrReceiverIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(1L),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.List.of());

        exchangeService.getMyExchanges(1L, 2, 30);

        ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(exchangeRepository).findByRequesterIdOrReceiverIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    void getSentExchanges_shouldQueryRequestedPage() {
        when(exchangeRepository.findByRequesterIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(1L), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.List.of());

        assertThat(exchangeService.getSentExchanges(1L, 0, 20)).isEmpty();

        verify(exchangeRepository).findByRequesterIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(1L), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void getReceivedExchanges_shouldQueryRequestedPage() {
        when(exchangeRepository.findByReceiverIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(1L), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.List.of());

        assertThat(exchangeService.getReceivedExchanges(1L, 0, 20)).isEmpty();

        verify(exchangeRepository).findByReceiverIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(1L), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void exchangeLists_shouldRejectSizeAboveMax() {
        assertThatThrownBy(() -> exchangeService.getMyExchanges(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> exchangeService.getSentExchanges(1L, 0, 101))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> exchangeService.getReceivedExchanges(1L, -1, 20))
                .isInstanceOf(BadRequestException.class);
    }


    // ------------------------------------------------------------ events

    @Test
    void createExchange_shouldPublishARequestedEvent() {
        User requester = User.builder().id(1L).build();
        User receiver = User.builder().id(2L).build();
        CreateExchangeRequest request = CreateExchangeRequest.builder()
                .receiverId(2L).offeredSkillId(10L).wantedSkillId(20L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(skillRepository.findById(10L)).thenReturn(Optional.of(Skill.builder().id(10L).build()));
        when(skillRepository.findById(20L)).thenReturn(Optional.of(Skill.builder().id(20L).build()));
        when(userSkillRepository.existsByUserIdAndSkillIdAndType(1L, 10L, SkillType.TEACH)).thenReturn(true);
        when(userSkillRepository.existsByUserIdAndSkillIdAndType(2L, 20L, SkillType.TEACH)).thenReturn(true);
        when(exchangeRepository.save(any(SkillExchange.class))).thenAnswer(invocation -> {
            SkillExchange e = invocation.getArgument(0);
            e.setId(77L);
            return e;
        });
        when(dtoMapper.toExchangeResponse(any(SkillExchange.class))).thenReturn(ExchangeResponse.builder().build());

        exchangeService.createExchange(1L, request);

        verify(eventPublisher).publishEvent(new com.sashank.paraspa.event.ExchangeRequestedEvent(77L, 1L, 2L));
    }

    @Test
    void createExchange_shouldPublishNothing_whenItIsRejected() {
        CreateExchangeRequest toSelf = CreateExchangeRequest.builder().receiverId(1L).offeredSkillId(10L).wantedSkillId(20L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> exchangeService.createExchange(1L, toSelf)).isInstanceOf(BadRequestException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void acceptExchange_shouldPublishAnAcceptedEventNamingTheRequesterAndTheAccepter() {
        SkillExchange exchange = SkillExchange.builder().id(100L)
                .requester(User.builder().id(1L).build())
                .receiver(User.builder().id(2L).build())
                .status(ExchangeStatus.PENDING).build();
        when(exchangeRepository.findById(100L)).thenReturn(Optional.of(exchange));
        when(exchangeRepository.save(any(SkillExchange.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toExchangeResponse(any(SkillExchange.class))).thenReturn(ExchangeResponse.builder().build());

        exchangeService.acceptExchange(2L, 100L);

        verify(eventPublisher).publishEvent(new com.sashank.paraspa.event.ExchangeAcceptedEvent(100L, 1L, 2L));
    }

    @Test
    void rejectAndCompleteExchange_shouldPublishNothing() {
        SkillExchange pending = SkillExchange.builder().id(100L)
                .requester(User.builder().id(1L).build()).receiver(User.builder().id(2L).build())
                .status(ExchangeStatus.PENDING).build();
        when(exchangeRepository.findById(100L)).thenReturn(Optional.of(pending));
        when(exchangeRepository.save(any(SkillExchange.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toExchangeResponse(any(SkillExchange.class))).thenReturn(ExchangeResponse.builder().build());

        exchangeService.rejectExchange(2L, 100L);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }


    // ------------------------------------------------------------ blocks

    @Test
    void createExchange_acrossABlock_isRefused_nothingSaved_noEvent() {
        CreateExchangeRequest request = CreateExchangeRequest.builder().receiverId(2L).offeredSkillId(10L).wantedSkillId(20L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));
        org.mockito.Mockito.doThrow(new BadRequestException("You have blocked this user. Unblock them first."))
                .when(blockService).requireNoBlock(1L, 2L);

        assertThatThrownBy(() -> exchangeService.createExchange(1L, request)).isInstanceOf(BadRequestException.class);

        verify(exchangeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void acceptExchange_requestedBeforeABlock_cannotBeAcceptedAfterwards() {
        SkillExchange exchange = SkillExchange.builder().id(100L)
                .requester(User.builder().id(1L).build()).receiver(User.builder().id(2L).build())
                .status(ExchangeStatus.PENDING).build();
        when(exchangeRepository.findById(100L)).thenReturn(Optional.of(exchange));
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("User not found")).when(blockService).requireNoBlock(2L, 1L);

        assertThatThrownBy(() -> exchangeService.acceptExchange(2L, 100L)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(exchange.getStatus()).isEqualTo(ExchangeStatus.PENDING);
        verify(exchangeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
