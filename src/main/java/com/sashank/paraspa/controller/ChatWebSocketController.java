package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.SendMessageRequest;
import com.sashank.paraspa.dto.response.MessageResponse;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.UserRepository;
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
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid SendMessageRequest request, Principal principal) {
        User sender = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
