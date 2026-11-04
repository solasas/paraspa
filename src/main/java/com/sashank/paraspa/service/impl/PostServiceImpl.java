package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.request.CreatePostRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostComment;
import com.sashank.paraspa.entity.PostLike;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.PostCommentRepository;
import com.sashank.paraspa.repository.PostLikeRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.PostService;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    public PostResponse createPost(Long userId, CreatePostRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Skill skill = skillRepository.findById(request.getSkillId())
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found"));

        Post post = Post.builder()
                .author(author)
                .skill(skill)
                .content(request.getContent().trim())
                .build();

        post = postRepository.save(post);
        return toResponse(post, userId);
    }

    @Override
    public PostResponse getPost(Long userId, Long postId) {
        return toResponse(findPost(postId), userId);
    }

    @Override
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = findPost(postId);

        if (!post.getAuthor().getId().equals(userId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        postLikeRepository.deleteByPostId(postId);
        postCommentRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Override
    public List<PostResponse> getExplorePosts(Long userId, Long skillId, int page, int size) {
        Pageable pageable = PaginationUtil.toPageable(page, size);
        List<Post> posts = skillId == null
                ? postRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : postRepository.findBySkillIdOrderByCreatedAtDescIdDesc(skillId, pageable);
        return toResponses(posts, userId);
    }

    @Override
    public List<PostResponse> getFeed(Long userId, Long skillId, int page, int size) {
        Pageable pageable = PaginationUtil.toPageable(page, size);

        List<Long> authorIds = new ArrayList<>(followRepository.findFollowingIdsByFollowerId(userId));
        authorIds.add(userId);

        List<Post> posts = skillId == null
                ? postRepository.findByAuthorIdInOrderByCreatedAtDescIdDesc(authorIds, pageable)
                : postRepository.findByAuthorIdInAndSkillIdOrderByCreatedAtDescIdDesc(authorIds, skillId, pageable);
        return toResponses(posts, userId);
    }

    @Override
    public List<PostResponse> getUserPosts(Long viewerId, Long authorId, int page, int size) {
        Pageable pageable = PaginationUtil.toPageable(page, size);

        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("User not found");
        }

        return toResponses(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(authorId, pageable), viewerId);
    }

    @Override
    public PostResponse likePost(Long userId, Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = findPost(postId);

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new AlreadyExistsException("You have already liked this post");
        }

        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
        return toResponse(post, userId);
    }

    @Override
    public void unlikePost(Long userId, Long postId) {
        findPost(postId);

        PostLike like = postLikeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not liked this post"));

        postLikeRepository.delete(like);
    }

    @Override
    public CommentResponse addComment(Long userId, Long postId, CreateCommentRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = findPost(postId);

        PostComment comment = PostComment.builder()
                .post(post)
                .author(author)
                .content(request.getContent().trim())
                .build();

        comment = postCommentRepository.save(comment);
        return dtoMapper.toCommentResponse(comment);
    }

    @Override
    public List<CommentResponse> getComments(Long postId) {
        findPost(postId);

        return postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(dtoMapper::toCommentResponse)
                .toList();
    }

    @Override
    public void deleteComment(Long userId, Long postId, Long commentId) {
        PostComment comment = postCommentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResourceNotFoundException("Comment not found");
        }

        postCommentRepository.delete(comment);
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private List<PostResponse> toResponses(List<Post> posts, Long viewerId) {
        return posts.stream()
                .map(post -> toResponse(post, viewerId))
                .toList();
    }

    private PostResponse toResponse(Post post, Long viewerId) {
        return dtoMapper.toPostResponse(
                post,
                postLikeRepository.countByPostId(post.getId()),
                postCommentRepository.countByPostId(post.getId()),
                postLikeRepository.existsByPostIdAndUserId(post.getId(), viewerId));
    }
}
