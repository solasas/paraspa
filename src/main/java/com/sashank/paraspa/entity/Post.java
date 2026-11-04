package com.sashank.paraspa.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Optional when the post has at least one image. Up to 2000 characters. */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** Up to 3 skill tags, in the order the author gave them. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany
    @JoinTable(name = "post_skills",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    @Builder.Default
    private List<Skill> skills = new ArrayList<>();

    /** Up to 4 images. Removing the post removes these rows (cascade). */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<PostMedia> media = new ArrayList<>();

    /** Maintained with atomic UPDATEs when posts are liked/unliked. */
    @Column(nullable = false)
    @Builder.Default
    private long likeCount = 0;

    /** Maintained with atomic UPDATEs when comments are added/removed. */
    @Column(nullable = false)
    @Builder.Default
    private long commentCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
