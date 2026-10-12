package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.AvailabilityResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.enums.SkillLevel;
import com.sashank.paraspa.service.AvailabilityService;
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

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(required = false) String skillName,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) SkillLevel level,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean availableOnly) {
        List<UserResponse> users = userSearchService.searchUsers(
                currentUserId, skillName, city, level, minRating, availableOnly);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}/availability")
    public ResponseEntity<List<AvailabilityResponse>> getUserAvailability(@PathVariable Long userId) {
        List<AvailabilityResponse> availability = availabilityService.getAvailability(userId);
        return ResponseEntity.ok(availability);
    }
}
