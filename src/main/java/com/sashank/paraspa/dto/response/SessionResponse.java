package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.enums.SessionMode;
import com.sashank.paraspa.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponse {
    private Long id;
    private ExchangeResponse exchange;
    private UserResponse scheduledBy;
    private LocalDateTime dateTime;
    private Integer durationMinutes;
    private SessionMode mode;
    private String meetLink;
    private String location;
    private String notes;
    private SessionStatus status;
    private LocalDateTime createdAt;
}

