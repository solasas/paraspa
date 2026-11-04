package com.sashank.paraspa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One issued refresh token. Only the SHA-256 hash of the value the client holds is stored. */
@Entity
@Table(name = "refresh_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refresh_tokens_hash", columnNames = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Set when the token was used (rotated), logged out, or revoked by a password change. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
