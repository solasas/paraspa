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
public class MatchResponse {
    private UserResponse user;
    private List<SkillResponse> theyCanTeachMe;

    @JsonProperty("iCanTeachThem")
    private List<SkillResponse> iCanTeachThem;

    @JsonProperty("isMutual")
    private boolean isMutual;
}

