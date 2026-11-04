package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.request.CreatePostRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import java.util.List;

public interface PostService {
    PostResponse createPost(Long userId, CreatePostRequest request);
    PostResponse getPost(Long userId, Long postId);
    void deletePost(Long userId, Long postId);
    List<PostResponse> getExplorePosts(Long userId, Long skillId, int page, int size);
    List<PostResponse> getFeed(Long userId, Long skillId, int page, int size);
    List<PostResponse> getUserPosts(Long viewerId, Long authorId, int page, int size);
    PostResponse likePost(Long userId, Long postId);
    void unlikePost(Long userId, Long postId);
    CommentResponse addComment(Long userId, Long postId, CreateCommentRequest request);
    List<CommentResponse> getComments(Long postId);
    void deleteComment(Long userId, Long postId, Long commentId);
}
