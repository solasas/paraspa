package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.CreateRatingRequest;
import com.sashank.paraspa.dto.response.RatingResponse;
import com.sashank.paraspa.dto.response.RatingSummaryResponse;
import com.sashank.paraspa.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RatingController {

    @Autowired
    private RatingService ratingService;

    @PostMapping("/sessions/{sessionId}/ratings")
    public ResponseEntity<RatingResponse> rateSession(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateRatingRequest request) {
        RatingResponse response = ratingService.rateSession(userId, sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/users/{userId}/ratings")
    public ResponseEntity<List<RatingResponse>> getUserRatings(
            @PathVariable Long userId) {
        List<RatingResponse> ratings = ratingService.getUserRatings(userId);
        return ResponseEntity.ok(ratings);
    }

    @GetMapping("/users/{userId}/rating-summary")
    public ResponseEntity<RatingSummaryResponse> getRatingSummary(
            @PathVariable Long userId) {
        RatingSummaryResponse summary = ratingService.getRatingSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/profile/me/ratings")
    public ResponseEntity<List<RatingResponse>> getMyReceivedRatings(
            @RequestAttribute("userId") Long userId) {
        List<RatingResponse> ratings = ratingService.getMyReceivedRatings(userId);
        return ResponseEntity.ok(ratings);
    }
}

