package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.SendMessageRequest;
import com.sashank.paraspa.dto.response.MessageResponse;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.exception.TooManyRequestsException;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.security.RateLimiter;
import com.sashank.paraspa.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class ChatWebSocketController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid SendMessageRequest request, Principal principal) {
        User sender = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // the same budget as POST /api/messages: chatting over the socket must not be a way around the limit
        RateLimiter.Decision decision = rateLimiter.tryAcquire(RateLimiter.Rule.MESSAGE, "user:" + sender.getId());
        if (!decision.allowed()) {
            throw new TooManyRequestsException("Too many messages. Try again in " + decision.retryAfterSeconds() + " seconds.", decision.retryAfterSeconds());
        }
        MessageResponse response = messageService.sendMessage(sender.getId(), request);

        messagingTemplate.convertAndSendToUser(response.getReceiver().getEmail(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(response.getSender().getEmail(), "/queue/messages", response);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception exception) {
        return exception.getMessage();
    }
}
