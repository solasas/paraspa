package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {
    private Long id;
    private UserResponse author;
    private SkillResponse skill;
    private String content;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
    private LocalDateTime createdAt;
}
