package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.entity.RefreshToken;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.UnauthorizedException;
import com.sashank.paraspa.repository.RefreshTokenRepository;
import com.sashank.paraspa.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String INVALID = "Invalid or expired refresh token";

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    /** Replaceable in tests. */
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];   // 256 bits from the OS CSPRNG
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .createdAt(now)
                .expiresAt(now.plusDays(refreshExpirationDays))
                .build());
        return raw;
    }

    /** noRollbackFor: the "revoke everything" done on reuse detection must survive the 401 that follows. */
    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException(INVALID);
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID));

        LocalDateTime now = LocalDateTime.now(clock);
        if (token.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException(INVALID);
        }
        User user = token.getUser();

        // exactly one caller wins this update; everybody else (a replay, or a thief racing the real client) gets 0
        if (refreshTokenRepository.revokeIfActive(token.getId(), now) == 0) {
            int revoked = refreshTokenRepository.revokeAllOfUser(user.getId(), now);
            log.warn("Refresh token reuse detected for user {}: revoked all {} active sessions", user.getId(), revoked);
            throw new UnauthorizedException(INVALID);
        }
        return user;
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> refreshTokenRepository.revokeIfActive(t.getId(), LocalDateTime.now(clock)));
    }

    @Override
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.revokeAllOfUser(userId, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public int purgeExpired() {
        // keep a week of expired rows so a replay of a just-expired token is still recognised as "known"
        return refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now(clock).minusDays(7));
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
