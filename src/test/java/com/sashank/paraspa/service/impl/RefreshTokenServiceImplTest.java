package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.entity.RefreshToken;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.UnauthorizedException;
import com.sashank.paraspa.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock private RefreshTokenRepository repository;
    @InjectMocks private RefreshTokenServiceImpl service;

    private final User user = User.builder().id(7L).email("a@example.com").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshExpirationDays", 30L);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RefreshToken stored(String raw, LocalDateTime expiresAt) {
        return RefreshToken.builder().id(1L).user(user).tokenHash(RefreshTokenServiceImpl.hash(raw))
                .createdAt(NOW_LOCAL.minusDays(1)).expiresAt(expiresAt).build();
    }

    // ------------------------------------------------------------------ issue

    @Test
    void issue_storesOnlyTheHash_andNeverTheRawValue() {
        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenServiceImpl.hash(raw)).hasSize(64).isNotEqualTo(raw);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW_LOCAL.plusDays(30));
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void issue_producesLongUniqueUrlSafeTokens() {
        String a = service.issue(user);
        String b = service.issue(user);

        assertThat(a).isNotEqualTo(b).hasSizeGreaterThanOrEqualTo(43).matches("[A-Za-z0-9_-]+");   // 256 bits, base64url
    }

    // ------------------------------------------------------------------ consume (rotation)

    @Test
    void consume_revokesTheTokenAndReturnsItsUser() {
        when(repository.findByTokenHash(RefreshTokenServiceImpl.hash("good"))).thenReturn(Optional.of(stored("good", NOW_LOCAL.plusDays(5))));
        when(repository.revokeIfActive(1L, NOW_LOCAL)).thenReturn(1);

        assertThat(service.consume("good")).isSameAs(user);
        verify(repository, never()).revokeAllOfUser(any(), any());
    }

    @Test
    void consume_unknownToken_is401() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("nope")).isInstanceOf(UnauthorizedException.class).hasMessage("Invalid or expired refresh token");
    }

    @Test
    void consume_blankOrMissing_is401_withoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.consume(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.consume("  ")).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    void consume_expiredToken_is401_andRevokesNothing() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(stored("old", NOW_LOCAL.minusSeconds(1))));

        assertThatThrownBy(() -> service.consume("old")).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).revokeIfActive(any(), any());
        verify(repository, never()).revokeAllOfUser(any(), any());
    }

    @Test
    void consume_aTokenThatWasAlreadyUsed_isTheftSignal_soEverySessionOfThatUserIsRevoked() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(stored("used", NOW_LOCAL.plusDays(5))));
        when(repository.revokeIfActive(1L, NOW_LOCAL)).thenReturn(0);   // somebody else got there first
        when(repository.revokeAllOfUser(7L, NOW_LOCAL)).thenReturn(3);

        assertThatThrownBy(() -> service.consume("used")).isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeAllOfUser(7L, NOW_LOCAL);
    }

    // ------------------------------------------------------------------ revoke / purge

    @Test
    void revoke_revokesAnActiveToken() {
        when(repository.findByTokenHash(RefreshTokenServiceImpl.hash("tok"))).thenReturn(Optional.of(stored("tok", NOW_LOCAL.plusDays(5))));

        service.revoke("tok");

        verify(repository).revokeIfActive(1L, NOW_LOCAL);
    }

    @Test
    void revoke_unknownBlankOrNullToken_isANoOp() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("unknown");
        service.revoke("");
        service.revoke(null);

        verify(repository, never()).revokeIfActive(any(), any());
    }

    @Test
    void revokeAll_revokesEveryActiveTokenOfTheUser() {
        service.revokeAll(7L);

        verify(repository).revokeAllOfUser(7L, NOW_LOCAL);
    }

    @Test
    void purgeExpired_keepsAWeekOfHistory() {
        when(repository.deleteExpiredBefore(eq(NOW_LOCAL.minusDays(7)))).thenReturn(4);

        assertThat(service.purgeExpired()).isEqualTo(4);
    }

    @Test
    void hash_isStableSha256Hex() {
        assertThat(RefreshTokenServiceImpl.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
