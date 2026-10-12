package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.sashank.paraspa.enums.SkillLevel;
import com.sashank.paraspa.enums.SkillType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddUserSkillRequest {
    @NotNull(message = "Skill ID is required")
    private Long skillId;

    @NotNull(message = "Skill type is required")
    private SkillType type;

    @NotNull(message = "Skill level is required")
    private SkillLevel level;
}

