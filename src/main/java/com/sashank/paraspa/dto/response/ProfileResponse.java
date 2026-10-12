package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String bio;
    private String city;
    private List<UserSkillResponse> teachSkills;
    private List<UserSkillResponse> learnSkills;
    private Double averageRating;
    private Long totalRatings;
}

