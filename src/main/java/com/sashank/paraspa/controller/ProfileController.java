package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.AddAvailabilityRequest;
import com.sashank.paraspa.dto.request.ChangePasswordRequest;
import com.sashank.paraspa.dto.request.UpdateProfileRequest;
import com.sashank.paraspa.dto.request.AddUserSkillRequest;
import com.sashank.paraspa.dto.response.AvailabilityResponse;
import com.sashank.paraspa.dto.response.ProfileResponse;
import com.sashank.paraspa.dto.response.UserSkillResponse;
import com.sashank.paraspa.service.AvailabilityService;
import com.sashank.paraspa.service.UserService;
import com.sashank.paraspa.service.UserSkillService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSkillService userSkillService;

    @Autowired
    private AvailabilityService availabilityService;

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile(@RequestAttribute("userId") Long userId) {
        ProfileResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse profile = userService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/skills")
    public ResponseEntity<UserSkillResponse> addUserSkill(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AddUserSkillRequest request) {
        UserSkillResponse response = userSkillService.addUserSkill(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/skills")
    public ResponseEntity<List<UserSkillResponse>> getUserSkills(
            @RequestAttribute("userId") Long userId) {
        List<UserSkillResponse> skills = userSkillService.getUserSkills(userId);
        return ResponseEntity.ok(skills);
    }

    @DeleteMapping("/skills/{userSkillId}")
    public ResponseEntity<Void> removeUserSkill(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long userSkillId) {
        userSkillService.removeUserSkill(userId, userSkillId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/availability")
    public ResponseEntity<AvailabilityResponse> addAvailability(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody AddAvailabilityRequest request) {
        AvailabilityResponse response = availabilityService.addAvailability(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/availability")
    public ResponseEntity<List<AvailabilityResponse>> getMyAvailability(
            @RequestAttribute("userId") Long userId) {
        List<AvailabilityResponse> availability = availabilityService.getAvailability(userId);
        return ResponseEntity.ok(availability);
    }

    @DeleteMapping("/availability/{availabilityId}")
    public ResponseEntity<Void> removeAvailability(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long availabilityId) {
        availabilityService.removeAvailability(userId, availabilityId);
        return ResponseEntity.noContent().build();
    }
}


