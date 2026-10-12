package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.AddUserSkillRequest;
import com.sashank.paraspa.dto.response.UserSkillResponse;
import java.util.List;

public interface UserSkillService {
    UserSkillResponse addUserSkill(Long userId, AddUserSkillRequest request);
    List<UserSkillResponse> getUserSkills(Long userId);
    void removeUserSkill(Long userId, Long userSkillId);
}

