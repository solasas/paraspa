package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface PostService {

    /**
     * Creates a post. Needs non-blank {@code content} and/or at least one image. Up to 2000 characters,
     * 3 skill tags (duplicates are ignored) and 4 images (JPG/PNG/WebP, 2 MB each). Empty file parts are ignored.
     */
    PostResponse createPost(Long userId, String content, List<Long> skillIds, List<MultipartFile> images);

    PostResponse getPost(Long userId, Long postId);

    /** Author only (anyone else gets a 404). Removes the post's likes, comments, image rows and image files. */
    void deletePost(Long userId, Long postId);

    List<PostResponse> getExplorePosts(Long userId, Long skillId, int page, int size);
    List<PostResponse> getFeed(Long userId, Long skillId, int page, int size);

    /** An author's posts, newest first, paged with a (createdAt, id) cursor. */
    CursorPageResponse<PostResponse> getUserPosts(Long viewerId, Long authorId, String cursor, int size);

    PostResponse likePost(Long userId, Long postId);
    void unlikePost(Long userId, Long postId);
    CommentResponse addComment(Long userId, Long postId, CreateCommentRequest request);
    List<CommentResponse> getComments(Long postId);
    void deleteComment(Long userId, Long postId, Long commentId);
}
