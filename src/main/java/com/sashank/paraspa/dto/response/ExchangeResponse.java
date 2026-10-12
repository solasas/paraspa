package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.enums.ExchangeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeResponse {
    private Long id;
    private UserResponse requester;
    private UserResponse receiver;
    private SkillResponse offeredSkill;
    private SkillResponse wantedSkill;
    private ExchangeStatus status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

