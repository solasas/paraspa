package com.sashank.paraspa.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    private String name;
    private String bio;
    private String city;

    /** Make the account private (true) or public (false). Omit to leave it unchanged. */
    @JsonProperty("isPrivate")
    private Boolean privateAccount;
}

