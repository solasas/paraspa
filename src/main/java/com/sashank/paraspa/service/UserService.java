package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.ChangePasswordRequest;
import com.sashank.paraspa.dto.request.UpdateProfileRequest;
import com.sashank.paraspa.dto.response.ProfileResponse;
import com.sashank.paraspa.entity.User;

public interface UserService {
    User getUserById(Long userId);
    ProfileResponse getProfile(Long userId);
    ProfileResponse updateProfile(Long userId, UpdateProfileRequest request);
    void changePassword(Long userId, ChangePasswordRequest request);
}

