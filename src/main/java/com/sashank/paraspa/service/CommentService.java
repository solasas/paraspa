package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.CursorPageResponse;

public interface CommentService {

    /**
     * Adds a comment, or a reply when {@code request.parentId} is set. A reply must target a top-level comment of
     * the same post (replies to replies are rejected with a 400). Counts towards the post's commentCount.
     */
    CommentResponse addComment(Long userId, Long postId, CreateCommentRequest request);

    /** A post's top-level comments, oldest first, each with its replies nested (oldest first), cursor-paginated. */
    CursorPageResponse<CommentResponse> getComments(Long postId, String cursor, int size);

    /**
     * Deletes a comment. Allowed for the comment's author and for the post's author; anyone else gets a 404.
     * Deleting a top-level comment also deletes its replies; the post's commentCount drops by all of them.
     */
    void deleteComment(Long userId, Long commentId);
}
