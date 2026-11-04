package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.enums.SkillLevel;
import java.util.List;

public interface UserSearchService {
    List<UserResponse> searchUsers(Long currentUserId, String skillName, String city,
                                    SkillLevel level, Double minRating, Boolean availableOnly);

    /** Same filters as above, returned in a stable order (by user id) one page at a time. */
    List<UserResponse> searchUsers(Long currentUserId, String skillName, String city,
                                    SkillLevel level, Double minRating, Boolean availableOnly,
                                    int page, int size);
}
