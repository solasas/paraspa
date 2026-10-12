package com.sashank.paraspa.dto.response;

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
public class UserSkillResponse {
    private Long id;
    private SkillResponse skill;
    private SkillType type;
    private SkillLevel level;
}

