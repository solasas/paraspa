package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.RegisterRequest;
import com.sashank.paraspa.dto.request.LoginRequest;
import com.sashank.paraspa.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}

