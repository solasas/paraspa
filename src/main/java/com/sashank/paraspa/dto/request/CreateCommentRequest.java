package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentRequest {
    @NotBlank(message = "Content is required")
    @Size(max = 500, message = "Comment must be at most 500 characters")
    private String content;

    /** Set to reply to a top-level comment of the same post; omit for a new top-level comment. */
    private Long parentId;
}
