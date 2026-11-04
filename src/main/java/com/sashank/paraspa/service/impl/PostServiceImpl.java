package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostComment;
import com.sashank.paraspa.entity.PostLike;
import com.sashank.paraspa.entity.PostMedia;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.PostCommentRepository;
import com.sashank.paraspa.repository.PostLikeRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.PostService;
import com.sashank.paraspa.storage.StorageService;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.ImageFormat;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PostServiceImpl implements PostService {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_SKILL_TAGS = 3;
    public static final int MAX_IMAGES = 4;
    public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

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
    private StorageService storageService;

    @Autowired
    private DtoMapper dtoMapper;

    // ------------------------------------------------------------------ create

    @Override
    @Transactional
    public PostResponse createPost(Long userId, String content, List<Long> skillIds, List<MultipartFile> images) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // 1. validate everything before touching storage or the database
        String text = content == null || content.isBlank() ? null : content.trim();
        if (text != null && text.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Content must be at most " + MAX_CONTENT_LENGTH + " characters");
        }

        List<MultipartFile> files = images == null ? List.of()
                : images.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (text == null && files.isEmpty()) {
            throw new BadRequestException("A post needs text or at least one image");
        }
        if (files.size() > MAX_IMAGES) {
            throw new BadRequestException("A post can have at most " + MAX_IMAGES + " images");
        }

        Set<Long> distinctSkillIds = skillIds == null ? Set.of() : new LinkedHashSet<>(skillIds);
        if (distinctSkillIds.size() > MAX_SKILL_TAGS) {
            throw new BadRequestException("A post can have at most " + MAX_SKILL_TAGS + " skill tags");
        }
        List<Skill> skills = new ArrayList<>();
        for (Long skillId : distinctSkillIds) {
            skills.add(skillRepository.findById(skillId)
                    .orElseThrow(() -> new ResourceNotFoundException("Skill not found")));
        }

        List<byte[]> contents = new ArrayList<>();
        List<ImageFormat> formats = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_IMAGE_BYTES) {
                throw new BadRequestException("Each image must be at most 2 MB");
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new BadRequestException("Could not read the uploaded image");
            }
            formats.add(ImageFormat.detect(bytes)
                    .orElseThrow(() -> new BadRequestException("Images must be JPG, PNG or WebP")));
            contents.add(bytes);
        }

        // 2. store the images, then save the post with its media rows; undo the files if anything fails
        List<String> storedUrls = new ArrayList<>();
        try {
            for (int i = 0; i < contents.size(); i++) {
                String filename = userId + "-" + UUID.randomUUID() + "." + formats.get(i).getExtension();
                storedUrls.add(storageService.store("posts", filename, contents.get(i)));
            }

            Post post = Post.builder()
                    .author(author)
                    .content(text)
                    .skills(skills)
                    .build();
            for (int i = 0; i < storedUrls.size(); i++) {
                post.getMedia().add(PostMedia.builder().post(post).url(storedUrls.get(i)).position(i).build());
            }

            post = postRepository.saveAndFlush(post);
            return toResponse(post, userId);
        } catch (RuntimeException e) {
            storedUrls.forEach(storageService::delete);
            throw e;
        }
    }

    // -------------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPost(Long userId, Long postId) {
        return toResponse(findPost(postId), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getExplorePosts(Long userId, Long skillId, int page, int size) {
        Pageable pageable = PaginationUtil.toPageable(page, size);
        List<Post> posts = skillId == null
                ? postRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : postRepository.findBySkills_IdOrderByCreatedAtDescIdDesc(skillId, pageable);
        return toResponses(posts, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getFeed(Long userId, Long skillId, int page, int size) {
        Pageable pageable = PaginationUtil.toPageable(page, size);

        List<Long> authorIds = new ArrayList<>(followRepository.findFollowingIdsByFollowerId(userId));
        authorIds.add(userId);

        List<Post> posts = skillId == null
                ? postRepository.findByAuthorIdInOrderByCreatedAtDescIdDesc(authorIds, pageable)
                : postRepository.findByAuthorIdInAndSkills_IdOrderByCreatedAtDescIdDesc(authorIds, skillId, pageable);
        return toResponses(posts, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> getUserPosts(Long viewerId, Long authorId, String cursor, int size) {
        PaginationUtil.requireValidSize(size);
        CursorUtil.Keyset after = CursorUtil.decodeKeyset(cursor);

        if (!userRepository.existsById(authorId)) {
            throw new ResourceNotFoundException("User not found");
        }

        // fetch one extra row: its presence tells us there is a next page
        Pageable pageable = PageRequest.of(0, size + 1);
        List<Post> fetched = after == null
                ? postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(authorId, pageable)
                : postRepository.findByAuthorIdBefore(authorId, after.createdAt(), after.id(), pageable);

        CursorPageResponse<Post> page = CursorPageResponse.ofCursor(fetched, size,
                post -> CursorUtil.encode(post.getCreatedAt(), post.getId()));

        return new CursorPageResponse<>(toResponses(page.getItems(), viewerId), page.getNextCursor());
    }

    // ------------------------------------------------------------------ delete

    @Override
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = findPost(postId);

        if (!post.getAuthor().getId().equals(userId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        List<String> mediaUrls = post.getMedia().stream().map(PostMedia::getUrl).toList();

        postLikeRepository.deleteByPostId(postId);
        postCommentRepository.deleteByPostId(postId);
        postRepository.delete(post);   // cascades to the media rows and the skill-tag links

        mediaUrls.forEach(storageService::delete);
    }

    // ------------------------------------------------------------------- likes

    @Override
    @Transactional
    public PostResponse likePost(Long userId, Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = findPost(postId);

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new AlreadyExistsException("You have already liked this post");
        }

        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
        postRepository.incrementLikeCount(postId);

        return toResponse(findPost(postId), userId);
    }

    @Override
    @Transactional
    public void unlikePost(Long userId, Long postId) {
        findPost(postId);

        PostLike like = postLikeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not liked this post"));

        postLikeRepository.delete(like);
        postRepository.decrementLikeCount(postId);
    }

    // ---------------------------------------------------------------- comments

    @Override
    @Transactional
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
        postRepository.incrementCommentCount(postId);
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
    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        PostComment comment = postCommentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResourceNotFoundException("Comment not found");
        }

        postCommentRepository.delete(comment);
        postRepository.decrementCommentCount(postId);
    }

    // ----------------------------------------------------------------- helpers

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }

    private PostResponse toResponse(Post post, Long viewerId) {
        return dtoMapper.toPostResponse(post, postLikeRepository.existsByPostIdAndUserId(post.getId(), viewerId));
    }

    /** Maps a page of posts with a single query for "which of these did the viewer like". */
    private List<PostResponse> toResponses(List<Post> posts, Long viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<Long> liked = Set.copyOf(postLikeRepository.findLikedPostIds(
                viewerId, posts.stream().map(Post::getId).toList()));
        return posts.stream()
                .map(post -> dtoMapper.toPostResponse(post, liked.contains(post.getId())))
                .toList();
    }
}
