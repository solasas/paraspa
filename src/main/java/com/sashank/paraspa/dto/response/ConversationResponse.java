package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationResponse {
    private UserResponse otherUser;
    private MessageResponse lastMessage;
    private long unreadCount;
}
