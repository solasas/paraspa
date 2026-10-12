package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.sashank.paraspa.enums.SkillCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSkillRequest {
    @NotBlank(message = "Skill name is required")
    private String name;

    @NotNull(message = "Skill category is required")
    private SkillCategory category;

    private String description;
}

