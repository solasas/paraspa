package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class FollowController {

    @Autowired
    private FollowService followService;

    @PutMapping("/{userId}/follow")
    public ResponseEntity<FollowResponse> followUser(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(followService.followUser(currentUserId, userId));
    }

    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<Void> unfollowUser(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        followService.unfollowUser(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(followService.getFollowers(userId, page, size));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(followService.getFollowing(userId, page, size));
    }

    @GetMapping("/{userId}/follow-status")
    public ResponseEntity<FollowStatusResponse> getFollowStatus(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowStatus(currentUserId, userId));
    }
}
