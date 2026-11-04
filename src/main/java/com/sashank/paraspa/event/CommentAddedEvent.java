package com.sashank.paraspa.event;

/**
 * Published when a comment or reply is added.
 *
 * @param parentCommentAuthorId author of the comment being replied to; null for a top-level comment
 */
public record CommentAddedEvent(Long postId, Long postAuthorId, Long commentId, Long authorId, Long parentCommentAuthorId) {
}
