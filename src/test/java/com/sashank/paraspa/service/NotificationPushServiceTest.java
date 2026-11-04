package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPushServiceTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationPushService pushService;

    @Test
    void push_sendsToTheUsersNotificationQueue_addressedByEmail() {
        NotificationResponse payload = NotificationResponse.builder().id(1L).type(NotificationType.LIKE).build();

        pushService.push("asha@example.com", payload);

        // the client subscribes to /user/queue/notifications; the broker routes it to this user's sessions
        verify(messagingTemplate).convertAndSendToUser("asha@example.com", "/queue/notifications", payload);
        assertThat(NotificationPushService.DESTINATION).isEqualTo("/queue/notifications");
    }

    @Test
    void push_neverThrows_aBrokenBrokerMustNotHideAStoredNotification() {
        doThrow(new MessagingException("broker down")).when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        assertThatCode(() -> pushService.push("asha@example.com", NotificationResponse.builder().id(1L).build()))
                .doesNotThrowAnyException();
    }
}
