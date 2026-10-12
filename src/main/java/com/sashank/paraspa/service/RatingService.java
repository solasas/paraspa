package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateRatingRequest;
import com.sashank.paraspa.dto.response.RatingResponse;
import com.sashank.paraspa.dto.response.RatingSummaryResponse;
import java.util.List;

public interface RatingService {
    RatingResponse rateSession(Long userId, Long sessionId, CreateRatingRequest request);
    List<RatingResponse> getUserRatings(Long userId);
    RatingSummaryResponse getRatingSummary(Long userId);
    List<RatingResponse> getMyReceivedRatings(Long userId);
}

