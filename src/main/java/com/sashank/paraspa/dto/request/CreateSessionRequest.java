package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.NotNull;
import com.sashank.paraspa.enums.SessionMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionRequest {
    @NotNull(message = "Date and time is required")
    private LocalDateTime dateTime;

    @NotNull(message = "Duration in minutes is required")
    private Integer durationMinutes;

    @NotNull(message = "Session mode is required")
    private SessionMode mode;

    private String meetLink;

    private String location;

    private String notes;
}

