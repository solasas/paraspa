package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingSummaryResponse {
    private Double averageStars;
    private Long totalRatings;
    private RatingBreakdown breakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RatingBreakdown {
        private Long oneStar;
        private Long twoStar;
        private Long threeStar;
        private Long fourStar;
        private Long fiveStar;
    }
}

