package com.sashank.paraspa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "post_media", uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_media_post_position", columnNames = {"post_id", "position"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // excluded from equals/hashCode/toString: Post.media points back here, which would recurse
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int position;
}
