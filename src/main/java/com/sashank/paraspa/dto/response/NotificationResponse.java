package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** One notification; the same shape in the REST list and in the WebSocket push. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private NotificationTargetType targetType;
    private Long targetId;
    /** Who did it. Public profile fields only (no email). */
    private UserResponse actor;
    private boolean read;
    private LocalDateTime createdAt;
}
