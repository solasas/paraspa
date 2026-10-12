package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRescheduleRequest {
    @NotNull(message = "Proposed date and time is required")
    private LocalDateTime proposedDateTime;

    private String reason;
}
