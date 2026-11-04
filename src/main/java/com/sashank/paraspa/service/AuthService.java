package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.RegisterRequest;
import com.sashank.paraspa.dto.request.LoginRequest;
import com.sashank.paraspa.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);

    /** Single-use rotation: the presented refresh token is revoked and a fresh access + refresh pair is returned. */
    AuthResponse refresh(String refreshToken);

    /** Revokes the refresh token (idempotent). */
    void logout(String refreshToken);
}

