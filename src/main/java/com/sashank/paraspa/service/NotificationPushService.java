package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes a notification to one user's open WebSocket sessions. The destination is {@code /user/queue/notifications}
 * from the client's point of view. The user is addressed by email because that is the STOMP principal name
 * (see {@code JwtChannelInterceptor}), the same as the chat does for {@code /user/queue/messages}.
 * A user who is not connected simply misses the push and finds the notification through the REST list.
 */
@Component
public class NotificationPushService {

    public static final String DESTINATION = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(NotificationPushService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /** Never throws: a failed push must not undo or hide a notification that is already stored. */
    public void push(String recipientEmail, NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(recipientEmail, DESTINATION, notification);
        } catch (Exception e) {
            log.warn("Could not push notification {} over WebSocket", notification.getId(), e);
        }
    }
}
