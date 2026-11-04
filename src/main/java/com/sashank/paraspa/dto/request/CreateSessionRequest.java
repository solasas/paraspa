package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
    @Future(message = "Session date and time must be in the future")
    private LocalDateTime dateTime;

    @NotNull(message = "Duration in minutes is required")
    @Positive(message = "Duration must be a positive number of minutes")
    private Integer durationMinutes;

    @NotNull(message = "Session mode is required")
    private SessionMode mode;

    private String meetLink;

    private String location;

    private String notes;
}

