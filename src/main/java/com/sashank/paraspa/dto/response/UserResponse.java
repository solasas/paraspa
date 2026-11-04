package com.sashank.paraspa.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String bio;
    private String city;
    private String avatarUrl;

    /** A private account: following it needs the owner's approval and its posts are for accepted followers only. */
    @JsonProperty("isPrivate")
    private boolean privateAccount;
    private Double averageRating;
    private Long totalRatings;
}

