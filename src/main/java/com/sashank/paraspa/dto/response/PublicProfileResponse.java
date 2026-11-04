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
    /** A private account: following it needs the owner's approval, and its posts are for accepted followers only. */
    @JsonProperty("isPrivate")
    private boolean privateAccount;
    private long followersCount;
    private long followingCount;
    @JsonProperty("isFollowedByMe")
    private boolean followedByMe;

    /** I asked to follow this private account and am waiting for the owner. */
    @JsonProperty("isFollowRequestedByMe")
    private boolean followRequestedByMe;
}
