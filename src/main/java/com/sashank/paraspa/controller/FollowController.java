package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatsResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class FollowController {

    @Autowired
    private FollowService followService;

    @PostMapping("/{userId}/follow")
    public ResponseEntity<FollowResponse> followUser(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        FollowResponse response = followService.followUser(currentUserId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<Void> unfollowUser(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        followService.unfollowUser(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowers(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowers(userId));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowing(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowing(userId));
    }

    @GetMapping("/{userId}/follow-stats")
    public ResponseEntity<FollowStatsResponse> getFollowStats(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowStats(currentUserId, userId));
    }
}
