package com.sashank.paraspa.entity;

import com.sashank.paraspa.enums.FollowStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Check;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Check(name = "chk_follows_not_self", constraints = "follower_id <> following_id")
@Table(name = "follows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_follows_follower_following", columnNames = {"follower_id", "following_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    /** PENDING while the owner of a private account has not answered; ACCEPTED otherwise. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FollowStatus status = FollowStatus.ACCEPTED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
