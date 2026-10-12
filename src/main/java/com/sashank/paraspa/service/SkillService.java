package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateSkillRequest;
import com.sashank.paraspa.dto.response.SkillResponse;
import com.sashank.paraspa.enums.SkillCategory;
import java.util.List;

public interface SkillService {
    SkillResponse createSkill(CreateSkillRequest request);
    List<SkillResponse> getAllSkills();
    List<SkillResponse> getSkillsByCategory(SkillCategory category);
    SkillResponse getSkillById(Long id);
}

