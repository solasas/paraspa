package com.sashank.paraspa.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** What any logged-in user may see about another user. Deliberately has no email field. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicProfileResponse {
    private Long id;
    private String name;
    private String bio;
    private String city;
    private String avatarUrl;
    private RatingSummaryResponse ratingSummary;
    private List<UserSkillResponse> teachSkills;
    private List<UserSkillResponse> learnSkills;
    private long followersCount;
    private long followingCount;
    @JsonProperty("isFollowedByMe")
    private boolean followedByMe;
}
