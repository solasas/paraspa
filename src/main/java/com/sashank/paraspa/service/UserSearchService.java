package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.enums.SkillLevel;
import java.util.List;

public interface UserSearchService {
    List<UserResponse> searchUsers(Long currentUserId, String skillName, String city,
                                    SkillLevel level, Double minRating, Boolean availableOnly);
}
