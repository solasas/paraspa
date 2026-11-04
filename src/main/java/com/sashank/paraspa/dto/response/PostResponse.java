package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private Long id;
    private UserResponse author;
    /** Null for image-only posts. */
    private String content;
    private List<SkillResponse> skills;
    private List<PostMediaResponse> media;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
    private LocalDateTime createdAt;
}
