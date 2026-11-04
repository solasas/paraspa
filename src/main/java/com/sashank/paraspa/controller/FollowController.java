package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.FollowRequestResponse;
import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FollowController {

    @Autowired
    private FollowService followService;

    @PutMapping("/users/{userId}/follow")
    public ResponseEntity<FollowResponse> followUser(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(followService.followUser(currentUserId, userId));
    }

    @DeleteMapping("/users/{userId}/follow")
    public ResponseEntity<Void> unfollowUser(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        followService.unfollowUser(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowers(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(followService.getFollowers(currentUserId, userId, page, size));
    }

    @GetMapping("/users/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowing(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(followService.getFollowing(currentUserId, userId, page, size));
    }

    @GetMapping("/users/{userId}/follow-status")
    public ResponseEntity<FollowStatusResponse> getFollowStatus(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowStatus(currentUserId, userId));
    }

    // ---- requests to follow my private account

    @GetMapping("/follow-requests")
    public ResponseEntity<List<FollowRequestResponse>> getFollowRequests(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(followService.getFollowRequests(currentUserId, page, size));
    }

    @PutMapping("/follow-requests/{requestId}/accept")
    public ResponseEntity<Void> acceptFollowRequest(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long requestId) {
        followService.acceptFollowRequest(currentUserId, requestId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/follow-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectFollowRequest(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long requestId) {
        followService.rejectFollowRequest(currentUserId, requestId);
        return ResponseEntity.noContent().build();
    }
}
