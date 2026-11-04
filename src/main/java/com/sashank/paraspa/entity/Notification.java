package com.sashank.paraspa.entity;

import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.time.LocalDateTime;

/**
 * "{@code actor} did something that concerns {@code recipient}". The pair ({@code targetType}, {@code targetId})
 * says what it is about: FOLLOW -> the follower (USER), LIKE / COMMENT / REPLY -> the post (POST),
 * EXCHANGE_REQUEST / EXCHANGE_ACCEPTED -> the exchange (EXCHANGE), MESSAGE -> the latest message (MESSAGE).
 */
@Entity
@Table(name = "notifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notifications_recipient_dedupe", columnNames = {"recipient_id", "dedupe_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    /** Only for notifications that may exist once (a like, a follow); null otherwise. See the migration. */
    @Column(name = "dedupe_key")
    private String dedupeKey;

    /** Not read-only: a collapsed MESSAGE notification is moved to the top again by refreshing this. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
