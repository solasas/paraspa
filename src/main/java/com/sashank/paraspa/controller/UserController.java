package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.AvailabilityResponse;
import com.sashank.paraspa.dto.response.PublicProfileResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.enums.SkillLevel;
import com.sashank.paraspa.service.AvailabilityService;
import com.sashank.paraspa.service.PublicProfileService;
import com.sashank.paraspa.service.UserSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserSearchService userSearchService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private PublicProfileService publicProfileService;

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(required = false) String skillName,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) SkillLevel level,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean availableOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<UserResponse> users = userSearchService.searchUsers(
                currentUserId, skillName, city, level, minRating, availableOnly, page, size);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PublicProfileResponse> getPublicProfile(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(publicProfileService.getPublicProfile(currentUserId, userId));
    }

    @GetMapping("/{userId}/availability")
    public ResponseEntity<List<AvailabilityResponse>> getUserAvailability(@PathVariable Long userId) {
        List<AvailabilityResponse> availability = availabilityService.getAvailability(userId);
        return ResponseEntity.ok(availability);
    }
}
