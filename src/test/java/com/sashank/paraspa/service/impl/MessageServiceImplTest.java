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
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DtoMapper dtoMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Test
    void sendMessage_shouldThrowBadRequestException_whenSenderEqualsReceiver() {
        Long userId = 1L;
        User user = User.builder().id(userId).build();

        SendMessageRequest request = SendMessageRequest.builder()
                .receiverId(userId)
                .content("Hey there")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> messageService.sendMessage(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot send a message to yourself");

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldThrowResourceNotFoundException_whenReceiverDoesNotExist() {
        Long senderId = 1L;
        Long receiverId = 2L;
        User sender = User.builder().id(senderId).build();

        SendMessageRequest request = SendMessageRequest.builder()
                .receiverId(receiverId)
                .content("Hey there")
                .build();

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(senderId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Receiver user not found");

        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldSaveMessageWithUnreadStatus() {
        Long senderId = 1L;
        Long receiverId = 2L;
        User sender = User.builder().id(senderId).build();
        User receiver = User.builder().id(receiverId).build();

        SendMessageRequest request = SendMessageRequest.builder()
                .receiverId(receiverId)
                .content("Hey, want to trade skills?")
                .build();

        when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
        when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dtoMapper.toMessageResponse(any(Message.class))).thenReturn(MessageResponse.builder().build());

        messageService.sendMessage(senderId, request);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());

        Message savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.isRead()).isFalse();
        assertThat(savedMessage.getSender()).isEqualTo(sender);
        assertThat(savedMessage.getReceiver()).isEqualTo(receiver);
        assertThat(savedMessage.getContent()).isEqualTo("Hey, want to trade skills?");
    }

    @Test
    void getConversation_shouldThrowResourceNotFoundException_whenOtherUserDoesNotExist() {
        Long userId = 1L;
        Long otherUserId = 2L;

        when(userRepository.findById(otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getConversation(userId, otherUserId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(messageRepository, never()).findConversation(anyLong(), anyLong(), any(Pageable.class));
    }

    @Test
    void getConversation_shouldMarkIncomingUnreadMessagesAsRead() {
        Long userId = 1L;
        Long otherUserId = 2L;
        User user = User.builder().id(userId).build();
        User otherUser = User.builder().id(otherUserId).build();

        Message incomingUnread = Message.builder()
                .id(10L)
                .sender(otherUser)
                .receiver(user)
                .content("Hi!")
                .read(false)
                .build();
        Message outgoingRead = Message.builder()
                .id(11L)
                .sender(user)
                .receiver(otherUser)
                .content("Hello!")
                .read(true)
                .build();

        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(messageRepository.findConversation(eq(userId), eq(otherUserId), any(Pageable.class)))
                .thenReturn(List.of(incomingUnread, outgoingRead));
        when(dtoMapper.toMessageResponse(any(Message.class))).thenReturn(MessageResponse.builder().build());

        messageService.getConversation(userId, otherUserId, 0, 20);

        ArgumentCaptor<List<Message>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(savedCaptor.capture());

        List<Message> savedMessages = savedCaptor.getValue();
        assertThat(savedMessages).hasSize(1);
        assertThat(savedMessages.get(0).getId()).isEqualTo(10L);
        assertThat(savedMessages.get(0).isRead()).isTrue();
    }

    @Test
    void getConversations_shouldGroupByOtherUserWithLastMessageAndUnreadCount() {
        Long userId = 1L;
        Long otherUserId = 2L;
        User user = User.builder().id(userId).build();
        User otherUser = User.builder().id(otherUserId).build();

        Message latest = Message.builder()
                .id(20L)
                .sender(otherUser)
                .receiver(user)
                .content("Latest")
                .read(false)
                .build();
        Message older = Message.builder()
                .id(19L)
                .sender(user)
                .receiver(otherUser)
                .content("Older")
                .read(true)
                .build();

        when(messageRepository.findAllForUser(userId)).thenReturn(List.of(latest, older));
        when(dtoMapper.toUserResponse(otherUser)).thenReturn(null);
        when(dtoMapper.toMessageResponse(latest)).thenReturn(MessageResponse.builder().id(20L).build());

        List<ConversationResponse> conversations = messageService.getConversations(userId);

        assertThat(conversations).hasSize(1);
        assertThat(conversations.get(0).getLastMessage().getId()).isEqualTo(20L);
        assertThat(conversations.get(0).getUnreadCount()).isEqualTo(1L);
    }

    @Test
    void getUnreadCount_shouldDelegateToRepository() {
        Long userId = 1L;
        when(messageRepository.countByReceiverIdAndReadFalse(userId)).thenReturn(5L);

        long count = messageService.getUnreadCount(userId);

        assertThat(count).isEqualTo(5L);
    }


    @Test
    void getConversation_shouldReturnPageOldestFirstEvenThoughRepositoryReturnsNewestFirst() {
        Long userId = 1L;
        Long otherUserId = 2L;
        User user = User.builder().id(userId).build();
        User otherUser = User.builder().id(otherUserId).build();

        Message older = Message.builder().id(10L).sender(user).receiver(otherUser).content("first").read(true).build();
        Message newer = Message.builder().id(11L).sender(user).receiver(otherUser).content("second").read(true).build();

        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        // repository contract: newest first
        when(messageRepository.findConversation(eq(userId), eq(otherUserId), any(Pageable.class)))
                .thenReturn(List.of(newer, older));
        when(dtoMapper.toMessageResponse(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            return MessageResponse.builder().id(m.getId()).build();
        });

        List<MessageResponse> result = messageService.getConversation(userId, otherUserId, 0, 20);

        assertThat(result).extracting(MessageResponse::getId).containsExactly(10L, 11L);
    }

    @Test
    void getConversation_shouldRequestTheGivenPageAndSize() {
        Long userId = 1L;
        Long otherUserId = 2L;

        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(User.builder().id(otherUserId).build()));
        when(messageRepository.findConversation(eq(userId), eq(otherUserId), any(Pageable.class)))
                .thenReturn(List.of());

        messageService.getConversation(userId, otherUserId, 3, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findConversation(eq(userId), eq(otherUserId), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getConversation_shouldRejectSizeAboveMax() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(User.builder().id(2L).build()));

        assertThatThrownBy(() -> messageService.getConversation(1L, 2L, 0, 101))
                .isInstanceOf(BadRequestException.class);

        verify(messageRepository, never()).findConversation(anyLong(), anyLong(), any(Pageable.class));
    }


    // ------------------------------------------------------------ events

    @Test
    void sendMessage_shouldPublishAMessageSentEvent() {
        User sender = User.builder().id(1L).build();
        User receiver = User.builder().id(2L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(900L);
            return m;
        });
        when(dtoMapper.toMessageResponse(any(Message.class))).thenReturn(MessageResponse.builder().build());

        messageService.sendMessage(1L, SendMessageRequest.builder().receiverId(2L).content("hi").build());

        verify(eventPublisher).publishEvent(new com.sashank.paraspa.event.MessageSentEvent(900L, 1L, 2L));
    }

    @Test
    void sendMessage_shouldPublishNothing_whenItIsRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));

        assertThatThrownBy(() -> messageService.sendMessage(1L, SendMessageRequest.builder().receiverId(1L).content("hi").build()))
                .isInstanceOf(BadRequestException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void getConversation_shouldPublishNothing() {
        User other = User.builder().id(2L).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(messageRepository.findConversation(eq(1L), eq(2L), any(Pageable.class))).thenReturn(List.of());

        messageService.getConversation(1L, 2L, 0, 20);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
