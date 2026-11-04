package com.sashank.paraspa.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** How the logged-in user and another user are related, plus that user's follow counts (accepted follows only). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowStatusResponse {
    private Long userId;

    /** True when the logged-in user follows {@code userId} (an accepted follow). */
    @JsonProperty("isFollowedByMe")
    private boolean followedByMe;

    /** True when the logged-in user asked to follow {@code userId} (a private account) and is waiting for approval. */
    @JsonProperty("isFollowRequestedByMe")
    private boolean followRequestedByMe;

    /** True when {@code userId} follows the logged-in user (an accepted follow). */
    @JsonProperty("isFollowingMe")
    private boolean followingMe;

    private long followersCount;
    private long followingCount;
}
