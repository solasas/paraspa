package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateExchangeRequest;
import com.sashank.paraspa.dto.response.ExchangeResponse;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.SkillExchange;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.ExchangeStatus;
import com.sashank.paraspa.enums.SkillType;
import com.sashank.paraspa.event.ExchangeAcceptedEvent;
import com.sashank.paraspa.event.ExchangeRequestedEvent;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.SkillExchangeRepository;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.repository.UserSkillRepository;
import com.sashank.paraspa.service.ExchangeService;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import com.sashank.paraspa.service.BlockService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ExchangeServiceImpl implements ExchangeService {

    @Autowired
    private SkillExchangeRepository exchangeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private BlockService blockService;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public ExchangeResponse createExchange(Long userId, CreateExchangeRequest request) {
        User requester = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver user not found"));

        if (requester.getId().equals(receiver.getId())) {
            throw new BadRequestException("Cannot send exchange request to yourself");
        }

        blockService.requireNoBlock(requester.getId(), receiver.getId());   // no exchanges across a block

        Skill offeredSkill = skillRepository.findById(request.getOfferedSkillId())
                .orElseThrow(() -> new ResourceNotFoundException("Offered skill not found"));

        Skill wantedSkill = skillRepository.findById(request.getWantedSkillId())
                .orElseThrow(() -> new ResourceNotFoundException("Wanted skill not found"));

        if (!userSkillRepository.existsByUserIdAndSkillIdAndType(requester.getId(), offeredSkill.getId(), SkillType.TEACH)) {
            throw new BadRequestException("You must be listed as a teacher of the offered skill");
        }

        if (!userSkillRepository.existsByUserIdAndSkillIdAndType(receiver.getId(), wantedSkill.getId(), SkillType.TEACH)) {
            throw new BadRequestException("Receiver does not teach the wanted skill");
        }

        SkillExchange exchange = SkillExchange.builder()
                .requester(requester)
                .receiver(receiver)
                .offeredSkill(offeredSkill)
                .wantedSkill(wantedSkill)
                .message(request.getMessage())
                .status(ExchangeStatus.PENDING)
                .build();

        exchange = exchangeRepository.save(exchange);
        eventPublisher.publishEvent(new ExchangeRequestedEvent(exchange.getId(), requester.getId(), receiver.getId()));
        return dtoMapper.toExchangeResponse(exchange);
    }

    @Override
    public List<ExchangeResponse> getMyExchanges(Long userId, int page, int size) {
        return exchangeRepository.findByRequesterIdOrReceiverIdOrderByCreatedAtDescIdDesc(
                        userId, userId, PaginationUtil.toPageable(page, size)).stream()
                .map(dtoMapper::toExchangeResponse)
                .toList();
    }

    @Override
    public List<ExchangeResponse> getSentExchanges(Long userId, int page, int size) {
        return exchangeRepository.findByRequesterIdOrderByCreatedAtDescIdDesc(
                        userId, PaginationUtil.toPageable(page, size)).stream()
                .map(dtoMapper::toExchangeResponse)
                .toList();
    }

    @Override
    public List<ExchangeResponse> getReceivedExchanges(Long userId, int page, int size) {
        return exchangeRepository.findByReceiverIdOrderByCreatedAtDescIdDesc(
                        userId, PaginationUtil.toPageable(page, size)).stream()
                .map(dtoMapper::toExchangeResponse)
                .toList();
    }

    @Override
    public ExchangeResponse getExchangeById(Long userId, Long exchangeId) {
        SkillExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange not found"));

        if (!exchange.getRequester().getId().equals(userId) && !exchange.getReceiver().getId().equals(userId)) {
            throw new ResourceNotFoundException("Exchange not found");
        }

        return dtoMapper.toExchangeResponse(exchange);
    }

    @Override
    public ExchangeResponse acceptExchange(Long userId, Long exchangeId) {
        SkillExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange not found"));

        if (!exchange.getReceiver().getId().equals(userId)) {
            throw new BadRequestException("Only receiver can accept exchange");
        }

        if (!exchange.getStatus().equals(ExchangeStatus.PENDING)) {
            throw new BadRequestException("Exchange is not in pending status");
        }
        blockService.requireNoBlock(userId, exchange.getRequester().getId());   // a request made before a block cannot be accepted

        exchange.setStatus(ExchangeStatus.ACCEPTED);
        exchange = exchangeRepository.save(exchange);
        eventPublisher.publishEvent(new ExchangeAcceptedEvent(exchange.getId(), exchange.getRequester().getId(), userId));
        return dtoMapper.toExchangeResponse(exchange);
    }

    @Override
    public ExchangeResponse rejectExchange(Long userId, Long exchangeId) {
        SkillExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange not found"));

        if (!exchange.getReceiver().getId().equals(userId)) {
            throw new BadRequestException("Only receiver can reject exchange");
        }

        if (!exchange.getStatus().equals(ExchangeStatus.PENDING)) {
            throw new BadRequestException("Exchange is not in pending status");
        }

        exchange.setStatus(ExchangeStatus.REJECTED);
        exchange = exchangeRepository.save(exchange);
        return dtoMapper.toExchangeResponse(exchange);
    }

    @Override
    public ExchangeResponse completeExchange(Long userId, Long exchangeId) {
        SkillExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange not found"));

        if (!exchange.getRequester().getId().equals(userId) && !exchange.getReceiver().getId().equals(userId)) {
            throw new BadRequestException("Only exchange participants can complete exchange");
        }

        if (!exchange.getStatus().equals(ExchangeStatus.ACCEPTED)) {
            throw new BadRequestException("Only accepted exchanges can be completed");
        }

        exchange.setStatus(ExchangeStatus.COMPLETED);
        exchange = exchangeRepository.save(exchange);
        return dtoMapper.toExchangeResponse(exchange);
    }
}

