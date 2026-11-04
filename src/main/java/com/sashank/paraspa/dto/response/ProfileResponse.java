package com.sashank.paraspa.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String avatarUrl;

    /** A private account: following it needs your approval, and your posts are for accepted followers only. */
    @JsonProperty("isPrivate")
    private boolean privateAccount;
    private List<UserSkillResponse> teachSkills;
    private List<UserSkillResponse> learnSkills;
    private Double averageRating;
    private Long totalRatings;
}

