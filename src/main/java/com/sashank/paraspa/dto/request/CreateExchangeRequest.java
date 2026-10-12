package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateExchangeRequest {
    @NotNull(message = "Receiver ID is required")
    private Long receiverId;

    @NotNull(message = "Offered skill ID is required")
    private Long offeredSkillId;

    @NotNull(message = "Wanted skill ID is required")
    private Long wantedSkillId;

    private String message;
}

