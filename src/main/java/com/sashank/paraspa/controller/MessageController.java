package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.SendMessageRequest;
import com.sashank.paraspa.dto.response.ConversationResponse;
import com.sashank.paraspa.dto.response.MessageResponse;
import com.sashank.paraspa.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody SendMessageRequest request) {
        MessageResponse response = messageService.sendMessage(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(
            @RequestAttribute("userId") Long userId) {
        List<ConversationResponse> conversations = messageService.getConversations(userId);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/conversations/{otherUserId}")
    public ResponseEntity<List<MessageResponse>> getConversation(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long otherUserId) {
        List<MessageResponse> messages = messageService.getConversation(userId, otherUserId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(messageService.getUnreadCount(userId));
    }
}
