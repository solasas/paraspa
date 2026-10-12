package com.sashank.paraspa.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String JWT_SECRET =
            "your-secret-key-change-this-in-production-use-a-long-secure-string-at-least-256-bits";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", JWT_SECRET);
    }

    @Test
    void validateToken_shouldReturnFalse_forExpiredToken() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", -1000L);

        String expiredToken = jwtTokenProvider.generateToken("jane@example.com", 1L);

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void getUserIdFromToken_shouldExtractCorrectUserId_fromValidToken() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 3600000L);
        Long userId = 42L;

        String token = jwtTokenProvider.generateToken("jane@example.com", userId);

        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(userId);
    }
}
