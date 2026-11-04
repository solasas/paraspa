package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    /** The short-lived access token (JWT). Send it as {@code Authorization: Bearer <token>}. */
    private String token;
    /** Opaque, single-use refresh token: exchange it at POST /api/auth/refresh for a new pair. */
    private String refreshToken;
    /** Lifetime of {@code token} in seconds. */
    private Long expiresIn;
    private String type;
    private Long id;
    private String name;
    private String email;
}

