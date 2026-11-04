package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.entity.Comment;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.event.CommentAddedEvent;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.CommentRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.CommentService;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import com.sashank.paraspa.service.AccessPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentServiceImpl implements CommentService {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AccessPolicy accessPolicy;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    @Transactional
    public CommentResponse addComment(Long userId, Long postId, CreateCommentRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        accessPolicy.requirePostVisible(userId, post);   // a blocked user's or a private account's post does not exist for you

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("Content is required");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Comment must be at most " + MAX_CONTENT_LENGTH + " characters");
        }

        Comment parent = null;
        if (request.getParentId() != null) {
            // a parent from another post is reported as missing, like any comment that is not visible here
            parent = commentRepository.findByIdAndPostId(request.getParentId(), postId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            if (parent.getParent() != null) {
                throw new BadRequestException("Replies can only be one level deep: reply to the top-level comment");
            }
        }

        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .author(author)
                .parent(parent)
                .content(content)
                .build());

        postRepository.incrementCommentCount(postId);
        eventPublisher.publishEvent(new CommentAddedEvent(postId, post.getAuthor().getId(), comment.getId(), userId,
                parent == null ? null : parent.getAuthor().getId()));
        return dtoMapper.toCommentResponse(comment);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> getComments(Long viewerId, Long postId, String cursor, int size) {
        PaginationUtil.requireValidSize(size);
        Long afterId = CursorUtil.decode(cursor);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        accessPolicy.requirePostVisible(viewerId, post);

        // one extra row tells us whether a next page exists; comments by users blocked with the viewer are left out
        Pageable pageable = PageRequest.of(0, size + 1);
        List<Comment> fetched = afterId == null
                ? commentRepository.findTopLevelVisible(postId, viewerId, pageable)
                : commentRepository.findTopLevelVisibleAfter(postId, afterId, viewerId, pageable);

        CursorPageResponse<Comment> page = CursorPageResponse.of(fetched, size, Comment::getId);

        // all replies for the page's comments in a single query, grouped by parent
        Map<Long, List<CommentResponse>> repliesByParent = new LinkedHashMap<>();
        if (!page.getItems().isEmpty()) {
            List<Long> parentIds = page.getItems().stream().map(Comment::getId).toList();
            for (Comment reply : commentRepository.findRepliesVisible(parentIds, viewerId)) {
                repliesByParent
                        .computeIfAbsent(reply.getParent().getId(), id -> new ArrayList<>())
                        .add(dtoMapper.toCommentResponse(reply));
            }
        }

        List<CommentResponse> items = new ArrayList<>();
        for (Comment comment : page.getItems()) {
            CommentResponse response = dtoMapper.toCommentResponse(comment);
            response.setReplies(repliesByParent.getOrDefault(comment.getId(), new ArrayList<>()));
            items.add(response);
        }
        return new CursorPageResponse<>(items, page.getNextCursor());
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        // locked: see CommentRepository.findByIdForUpdate (keeps the reply count below exact under concurrency)
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        Post post = comment.getPost();
        boolean isCommentAuthor = comment.getAuthor().getId().equals(userId);
        boolean isPostAuthor = post.getAuthor().getId().equals(userId);
        if (!isCommentAuthor && !isPostAuthor) {
            throw new ResourceNotFoundException("Comment not found");
        }

        // a top-level comment takes its replies with it (database cascade), so they leave the count too
        long removed = 1 + (comment.getParent() == null ? commentRepository.countByParentId(commentId) : 0);

        commentRepository.delete(comment);
        postRepository.decrementCommentCount(post.getId(), removed);
    }
}
