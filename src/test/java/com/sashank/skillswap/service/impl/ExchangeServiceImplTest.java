package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.request.CreateExchangeRequest;
import com.sashank.skillswap.dto.response.ExchangeResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.SkillExchange;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.enums.ExchangeStatus;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.exception.BadRequestException;
import com.sashank.skillswap.repository.SkillExchangeRepository;
import com.sashank.skillswap.repository.SkillRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.util.DtoMapper;
import com.sashank.skillswap.exception.ResourceNotFoundException;
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
}
