package com.sashank.paraspa.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {
    private Long id;
    private Long postId;
    /** Null for a top-level comment. */
    private Long parentId;
    private UserResponse author;
    private String content;
    private LocalDateTime createdAt;
    /** Replies, oldest first. Filled in when listing a post's comments; always empty on a reply. */
    @Builder.Default
    private List<CommentResponse> replies = new ArrayList<>();
}
