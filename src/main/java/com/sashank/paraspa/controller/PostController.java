package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.CreatePostRequest;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.service.PostService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {

    @Autowired
    private PostService postService;

    /** Text-only post (JSON). */
    @PostMapping(value = "/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreatePostRequest request) {
        PostResponse response = postService.createPost(userId, request.getContent(), request.getSkillIds(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Post with up to 4 images (multipart/form-data): parts "content", repeated "skillIds", repeated "images". */
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPostWithImages(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) List<Long> skillIds,
            @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        PostResponse response = postService.createPost(userId, content, skillIds, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getExplorePosts(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long skillId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getExplorePosts(userId, skillId, page, size));
    }

    @GetMapping("/posts/feed")
    public ResponseEntity<List<PostResponse>> getFeed(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long skillId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getFeed(userId, skillId, page, size));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponse> getPost(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(userId, postId));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId) {
        postService.deletePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{authorId}/posts")
    public ResponseEntity<CursorPageResponse<PostResponse>> getUserPosts(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long authorId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getUserPosts(userId, authorId, cursor, size));
    }

    /** Idempotent: liking an already liked post is a no-op that still returns 200 with the current counts. */
    @PutMapping("/posts/{postId}/like")
    public ResponseEntity<PostResponse> likePost(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.likePost(userId, postId));
    }

    /** Idempotent: removing a like you do not have is a no-op (204). */
    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlikePost(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId) {
        postService.unlikePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/{postId}/likes")
    public ResponseEntity<List<UserResponse>> getLikes(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getLikes(userId, postId, page, size));
    }
}
