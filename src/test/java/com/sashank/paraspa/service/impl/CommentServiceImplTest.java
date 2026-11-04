package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.entity.Comment;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.CommentRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private DtoMapper dtoMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CommentServiceImpl commentService;

    private final User me = User.builder().id(1L).build();
    private final User postOwner = User.builder().id(2L).build();
    private final User stranger = User.builder().id(3L).build();
    private final Post post = Post.builder().id(100L).author(postOwner).build();

    private static CreateCommentRequest request(String content, Long parentId) {
        return CreateCommentRequest.builder().content(content).parentId(parentId).build();
    }

    private static Comment comment(long id, Post post, User author, Comment parent) {
        return Comment.builder().id(id).post(post).author(author).parent(parent).content("c" + id).build();
    }

    @BeforeEach
    void defaultMapping() {
        // maps comments to responses the way the real mapper does (id, parentId, empty replies)
        org.mockito.Mockito.lenient().when(dtoMapper.toCommentResponse(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            return CommentResponse.builder().id(c.getId()).content(c.getContent())
                    .parentId(c.getParent() == null ? null : c.getParent().getId())
                    .replies(new ArrayList<>()).build();
        });
    }

    // ================================================================ adding

    private void stubPost() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment c = invocation.getArgument(0);
            c.setId(50L);
            return c;
        });
    }

    @Test
    void addComment_shouldSaveATrimmedTopLevelCommentAndIncrementTheCounter() {
        stubPost();

        commentService.addComment(1L, 100L, request("  nice one  ", null));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        InOrder order = inOrder(commentRepository, postRepository);
        order.verify(commentRepository).save(saved.capture());
        order.verify(postRepository).incrementCommentCount(100L);
        assertThat(saved.getValue().getContent()).isEqualTo("nice one");
        assertThat(saved.getValue().getAuthor()).isEqualTo(me);
        assertThat(saved.getValue().getPost()).isEqualTo(post);
        assertThat(saved.getValue().getParent()).isNull();
    }

    @Test
    void addComment_shouldAcceptExactly500CharactersAndRejectMore() {
        stubPost();
        commentService.addComment(1L, 100L, request("x".repeat(500), null));
        verify(commentRepository).save(any(Comment.class));

        assertThatThrownBy(() -> commentService.addComment(1L, 100L, request("x".repeat(501), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("500");
        verify(commentRepository, org.mockito.Mockito.times(1)).save(any(Comment.class));
    }

    @Test
    void addComment_shouldRejectBlankContent() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.addComment(1L, 100L, request("   ", null)))
                .isInstanceOf(BadRequestException.class);

        verify(commentRepository, never()).save(any());
        verify(postRepository, never()).incrementCommentCount(any());
    }

    @Test
    void addComment_shouldSaveAReplyToATopLevelCommentAndIncrementTheCounter() {
        stubPost();
        Comment topLevel = comment(10L, post, stranger, null);
        when(commentRepository.findByIdAndPostId(10L, 100L)).thenReturn(Optional.of(topLevel));

        CommentResponse response = commentService.addComment(1L, 100L, request("agreed", 10L));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getParent()).isSameAs(topLevel);
        assertThat(response.getParentId()).isEqualTo(10L);
        verify(postRepository).incrementCommentCount(100L);   // replies count towards commentCount too
    }

    @Test
    void addComment_shouldRejectAReplyToAReply_andChangeNothing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        Comment topLevel = comment(10L, post, stranger, null);
        Comment reply = comment(11L, post, stranger, topLevel);
        when(commentRepository.findByIdAndPostId(11L, 100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.addComment(1L, 100L, request("too deep", 11L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Replies can only be one level deep: reply to the top-level comment");

        verify(commentRepository, never()).save(any());
        verify(postRepository, never()).incrementCommentCount(any());
    }

    @Test
    void addComment_shouldTreatAParentFromAnotherPostAsMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        // the parent exists, but on a different post: the lookup is scoped to this post, so it finds nothing
        when(commentRepository.findByIdAndPostId(77L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.addComment(1L, 100L, request("hi", 77L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Parent comment not found");

        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_shouldThrowResourceNotFoundException_whenPostOrUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(postRepository.findById(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> commentService.addComment(1L, 100L, request("hi", null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Post not found");

        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> commentService.addComment(9L, 100L, request("hi", null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
    }

    // ================================================================ listing

    @Test
    void getComments_firstPageShouldFetchOneExtraRowAndNestRepliesOldestFirst() {
        Comment c1 = comment(10L, post, me, null);
        Comment c2 = comment(20L, post, me, null);
        Comment c3 = comment(30L, post, me, null);   // the extra row: only signals "there is more"
        Comment r1 = comment(11L, post, stranger, c1);
        Comment r2 = comment(12L, post, me, c1);
        Comment r3 = comment(21L, post, stranger, c2);
        when(postRepository.existsById(100L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNullOrderByIdAsc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(c1, c2, c3));
        when(commentRepository.findByParentIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(r1, r2, r3));

        CursorPageResponse<CommentResponse> page = commentService.getComments(100L, null, 2);

        assertThat(page.getItems()).extracting(CommentResponse::getId).containsExactly(10L, 20L);
        assertThat(page.getItems().get(0).getReplies()).extracting(CommentResponse::getId).containsExactly(11L, 12L);
        assertThat(page.getItems().get(1).getReplies()).extracting(CommentResponse::getId).containsExactly(21L);
        assertThat(CursorUtil.decode(page.getNextCursor())).isEqualTo(20L);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByPostIdAndParentIsNullOrderByIdAsc(eq(100L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void getComments_shouldFetchRepliesOnlyForTheCommentsOnThePage_inOneQuery() {
        Comment c1 = comment(10L, post, me, null);
        Comment c2 = comment(20L, post, me, null);
        Comment extra = comment(30L, post, me, null);
        when(postRepository.existsById(100L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNullOrderByIdAsc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(c1, c2, extra));
        when(commentRepository.findByParentIdInOrderByIdAsc(anyCollection())).thenReturn(List.of());

        commentService.getComments(100L, null, 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> parents = ArgumentCaptor.forClass(Collection.class);
        verify(commentRepository, org.mockito.Mockito.times(1)).findByParentIdInOrderByIdAsc(parents.capture());
        assertThat(parents.getValue()).containsExactlyInAnyOrder(10L, 20L);   // not the dropped extra row
    }

    @Test
    void getComments_commentWithoutRepliesShouldHaveAnEmptyReplyList() {
        Comment c1 = comment(10L, post, me, null);
        when(postRepository.existsById(100L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNullOrderByIdAsc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(c1));
        when(commentRepository.findByParentIdInOrderByIdAsc(anyCollection())).thenReturn(List.of());

        CursorPageResponse<CommentResponse> page = commentService.getComments(100L, null, 20);

        assertThat(page.getItems().get(0).getReplies()).isNotNull().isEmpty();
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void getComments_shouldContinueAfterTheCursor() {
        Comment c3 = comment(30L, post, me, null);
        when(postRepository.existsById(100L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNullAndIdGreaterThanOrderByIdAsc(eq(100L), eq(20L), any(Pageable.class)))
                .thenReturn(List.of(c3));
        when(commentRepository.findByParentIdInOrderByIdAsc(anyCollection())).thenReturn(List.of());

        CursorPageResponse<CommentResponse> page = commentService.getComments(100L, CursorUtil.encode(20L), 20);

        assertThat(page.getItems()).extracting(CommentResponse::getId).containsExactly(30L);
        assertThat(page.getNextCursor()).isNull();
        verify(commentRepository, never()).findByPostIdAndParentIsNullOrderByIdAsc(any(), any());
    }

    @Test
    void getComments_pagingThroughEverythingShouldVisitEachTopLevelCommentOnceInOrder() {
        List<Comment> all = List.of(comment(10L, post, me, null), comment(20L, post, me, null),
                comment(30L, post, me, null), comment(40L, post, me, null), comment(50L, post, me, null));
        when(postRepository.existsById(100L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNullOrderByIdAsc(eq(100L), any(Pageable.class)))
                .thenAnswer(invocation -> all.stream().limit(((Pageable) invocation.getArgument(1)).getPageSize()).toList());
        when(commentRepository.findByPostIdAndParentIsNullAndIdGreaterThanOrderByIdAsc(eq(100L), anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> all.stream().filter(c -> c.getId() > (Long) invocation.getArgument(1))
                        .limit(((Pageable) invocation.getArgument(2)).getPageSize()).toList());
        when(commentRepository.findByParentIdInOrderByIdAsc(anyCollection())).thenReturn(List.of());

        List<Long> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            CursorPageResponse<CommentResponse> page = commentService.getComments(100L, cursor, 2);
            page.getItems().forEach(c -> seen.add(c.getId()));
            cursor = page.getNextCursor();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(seen).containsExactly(10L, 20L, 30L, 40L, 50L);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void getComments_emptyPostShouldNotRunTheRepliesQuery() {
        when(postRepository.existsById(100L)).thenReturn(true);
        when(commentRepository.findByPostIdAndParentIsNullOrderByIdAsc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of());

        CursorPageResponse<CommentResponse> page = commentService.getComments(100L, null, 20);

        assertThat(page.getItems()).isEmpty();
        verify(commentRepository, never()).findByParentIdInOrderByIdAsc(any());
    }

    @Test
    void getComments_shouldRejectBadCursorBadSizeAndMissingPost() {
        assertThatThrownBy(() -> commentService.getComments(100L, "not-a-cursor!", 20))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> commentService.getComments(100L, null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> commentService.getComments(100L, null, 101)).isInstanceOf(BadRequestException.class);

        when(postRepository.existsById(100L)).thenReturn(false);
        assertThatThrownBy(() -> commentService.getComments(100L, null, 20))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Post not found");
    }

    // ============================================================== deleting

    @Test
    void deleteComment_authorCanDeleteTheirOwnReply_countDropsByOne() {
        Comment topLevel = comment(10L, post, stranger, null);
        Comment reply = comment(11L, post, me, topLevel);
        when(commentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(reply));

        commentService.deleteComment(1L, 11L);

        verify(commentRepository).delete(reply);
        verify(postRepository).decrementCommentCount(100L, 1L);
        verify(commentRepository, never()).countByParentId(any());   // a reply has no replies of its own
    }

    @Test
    void deleteComment_authorOfATopLevelCommentTakesItsRepliesWithIt() {
        Comment topLevel = comment(10L, post, me, null);
        when(commentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(topLevel));
        when(commentRepository.countByParentId(10L)).thenReturn(3L);

        commentService.deleteComment(1L, 10L);

        verify(commentRepository).delete(topLevel);
        verify(postRepository).decrementCommentCount(100L, 4L);   // the comment + its 3 replies
    }

    @Test
    void deleteComment_postAuthorCanDeleteAnyonesComment() {
        Comment byStranger = comment(10L, post, stranger, null);
        when(commentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(byStranger));
        when(commentRepository.countByParentId(10L)).thenReturn(0L);

        commentService.deleteComment(2L, 10L);   // user 2 owns the post

        verify(commentRepository).delete(byStranger);
        verify(postRepository).decrementCommentCount(100L, 1L);
    }

    @Test
    void deleteComment_postAuthorCanDeleteAReplyOnTheirPost() {
        Comment topLevel = comment(10L, post, stranger, null);
        Comment reply = comment(11L, post, stranger, topLevel);
        when(commentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(reply));

        commentService.deleteComment(2L, 11L);

        verify(commentRepository).delete(reply);
    }

    @Test
    void deleteComment_someoneElseGetsA404_andNothingChanges() {
        Comment byMe = comment(10L, post, me, null);
        when(commentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(byMe));

        // user 3 wrote neither the comment nor the post
        assertThatThrownBy(() -> commentService.deleteComment(3L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Comment not found");

        verify(commentRepository, never()).delete(any());
        verify(postRepository, never()).decrementCommentCount(any(), anyLong());
    }

    @Test
    void deleteComment_aCommentAuthorWhoDoesNotOwnThePostCannotDeleteSomeoneElsesComment() {
        Comment byStranger = comment(10L, post, stranger, null);
        when(commentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(byStranger));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L))   // user 1 is just another commenter
                .isInstanceOf(ResourceNotFoundException.class);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_shouldThrowResourceNotFoundException_whenMissing() {
        when(commentRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Comment not found");

        verify(postRepository, never()).decrementCommentCount(any(), anyLong());
    }


    // ------------------------------------------------------------ events

    @Test
    void addComment_shouldPublishACommentEventForATopLevelComment() {
        stubPost();

        commentService.addComment(1L, 100L, request("nice", null));

        // post 100 is by user 2; the new comment (id 50) is by user 1; no parent
        verify(eventPublisher).publishEvent(new com.sashank.paraspa.event.CommentAddedEvent(100L, 2L, 50L, 1L, null));
    }

    @Test
    void addComment_shouldPublishACommentEventNamingTheParentsAuthorForAReply() {
        stubPost();
        Comment topLevel = comment(10L, post, stranger, null);   // written by user 3
        when(commentRepository.findByIdAndPostId(10L, 100L)).thenReturn(Optional.of(topLevel));

        commentService.addComment(1L, 100L, request("agreed", 10L));

        verify(eventPublisher).publishEvent(new com.sashank.paraspa.event.CommentAddedEvent(100L, 2L, 50L, 1L, 3L));
    }

    @Test
    void addComment_shouldPublishNothing_whenTheReplyIsRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        Comment reply = comment(11L, post, stranger, comment(10L, post, stranger, null));
        when(commentRepository.findByIdAndPostId(11L, 100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.addComment(1L, 100L, request("too deep", 11L)))
                .isInstanceOf(BadRequestException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void deleteComment_shouldPublishNothing() {
        Comment byMe = comment(10L, post, me, null);
        when(commentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(byMe));
        when(commentRepository.countByParentId(10L)).thenReturn(0L);

        commentService.deleteComment(1L, 10L);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
