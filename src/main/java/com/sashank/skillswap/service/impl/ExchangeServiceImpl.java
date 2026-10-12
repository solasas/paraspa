package com.sashank.skillswap.service.impl;

import com.sashank.skillswap.dto.request.CreateExchangeRequest;
import com.sashank.skillswap.dto.response.ExchangeResponse;
import com.sashank.skillswap.entity.Skill;
import com.sashank.skillswap.entity.SkillExchange;
import com.sashank.skillswap.entity.User;
import com.sashank.skillswap.enums.ExchangeStatus;
import com.sashank.skillswap.enums.SkillType;
import com.sashank.skillswap.exception.BadRequestException;
import com.sashank.skillswap.exception.ResourceNotFoundException;
import com.sashank.skillswap.repository.SkillExchangeRepository;
import com.sashank.skillswap.repository.SkillRepository;
import com.sashank.skillswap.repository.UserRepository;
import com.sashank.skillswap.repository.UserSkillRepository;
import com.sashank.skillswap.service.ExchangeService;
import com.sashank.skillswap.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
        return dtoMapper.toExchangeResponse(exchange);
    }

    @Override
    public List<ExchangeResponse> getMyExchanges(Long userId) {
        return exchangeRepository.findByRequesterIdOrReceiverId(userId, userId).stream()
                .map(dtoMapper::toExchangeResponse)
                .toList();
    }

    @Override
    public List<ExchangeResponse> getSentExchanges(Long userId) {
        return exchangeRepository.findByRequesterId(userId).stream()
                .map(dtoMapper::toExchangeResponse)
                .toList();
    }

    @Override
    public List<ExchangeResponse> getReceivedExchanges(Long userId) {
        return exchangeRepository.findByReceiverId(userId).stream()
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

        exchange.setStatus(ExchangeStatus.ACCEPTED);
        exchange = exchangeRepository.save(exchange);
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

