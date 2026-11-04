package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.request.CreatePostRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.service.PostService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {

    @Autowired
    private PostService postService;

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreatePostRequest request) {
        PostResponse response = postService.createPost(userId, request);
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
    public ResponseEntity<List<PostResponse>> getUserPosts(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getUserPosts(userId, authorId, page, size));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<PostResponse> likePost(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId) {
        PostResponse response = postService.likePost(userId, postId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlikePost(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId) {
        postService.unlikePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = postService.addComment(userId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getComments(postId));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        postService.deleteComment(userId, postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
