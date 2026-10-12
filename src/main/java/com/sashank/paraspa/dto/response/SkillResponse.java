package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.enums.SkillCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillResponse {
    private Long id;
    private String name;
    private SkillCategory category;
    private String description;
}

