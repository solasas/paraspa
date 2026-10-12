package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.SendMessageRequest;
import com.sashank.paraspa.dto.response.ConversationResponse;
import com.sashank.paraspa.dto.response.MessageResponse;
import com.sashank.paraspa.entity.Message;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.MessageRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.MessageService;
import com.sashank.paraspa.util.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageServiceImpl implements MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public MessageResponse sendMessage(Long senderId, SendMessageRequest request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (senderId.equals(request.getReceiverId())) {
            throw new BadRequestException("Cannot send a message to yourself");
        }

        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver user not found"));

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(request.getContent())
                .read(false)
                .build();

        message = messageRepository.save(message);
        return dtoMapper.toMessageResponse(message);
    }

    @Override
    public List<MessageResponse> getConversation(Long userId, Long otherUserId) {
        userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Message> messages = messageRepository.findConversation(userId, otherUserId);

        List<Message> unreadFromOtherUser = messages.stream()
                .filter(m -> !m.isRead()
                        && m.getReceiver().getId().equals(userId)
                        && m.getSender().getId().equals(otherUserId))
                .toList();

        if (!unreadFromOtherUser.isEmpty()) {
            unreadFromOtherUser.forEach(m -> m.setRead(true));
            messageRepository.saveAll(unreadFromOtherUser);
        }

        return messages.stream()
                .map(dtoMapper::toMessageResponse)
                .toList();
    }

    @Override
    public List<ConversationResponse> getConversations(Long userId) {
        List<Message> messages = messageRepository.findAllForUser(userId);

        Map<Long, Message> lastMessageByOtherUser = new LinkedHashMap<>();
        for (Message message : messages) {
            Long otherUserId = message.getSender().getId().equals(userId)
                    ? message.getReceiver().getId()
                    : message.getSender().getId();
            lastMessageByOtherUser.putIfAbsent(otherUserId, message);
        }

        List<ConversationResponse> conversations = new ArrayList<>();
        for (Map.Entry<Long, Message> entry : lastMessageByOtherUser.entrySet()) {
            Long otherUserId = entry.getKey();
            Message lastMessage = entry.getValue();
            User otherUser = lastMessage.getSender().getId().equals(userId)
                    ? lastMessage.getReceiver()
                    : lastMessage.getSender();

            long unreadCount = messages.stream()
                    .filter(m -> !m.isRead()
                            && m.getReceiver().getId().equals(userId)
                            && m.getSender().getId().equals(otherUserId))
                    .count();

            conversations.add(ConversationResponse.builder()
                    .otherUser(dtoMapper.toUserResponse(otherUser))
                    .lastMessage(dtoMapper.toMessageResponse(lastMessage))
                    .unreadCount(unreadCount)
                    .build());
        }

        return conversations;
    }

    @Override
    public long getUnreadCount(Long userId) {
        return messageRepository.countByReceiverIdAndReadFalse(userId);
    }
}
