package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.SendMessageRequest;
import com.sashank.paraspa.dto.response.ConversationResponse;
import com.sashank.paraspa.dto.response.MessageResponse;
import java.util.List;

public interface MessageService {
    MessageResponse sendMessage(Long senderId, SendMessageRequest request);
    List<MessageResponse> getConversation(Long userId, Long otherUserId);
    List<ConversationResponse> getConversations(Long userId);
    long getUnreadCount(Long userId);
}
