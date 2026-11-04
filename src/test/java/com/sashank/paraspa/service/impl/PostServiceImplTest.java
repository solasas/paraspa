package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.request.CreatePostRequest;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostComment;
import com.sashank.paraspa.entity.PostLike;
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
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private DtoMapper dtoMapper;

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    void createPost_shouldThrowResourceNotFoundException_whenSkillMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(skillRepository.findById(5L)).thenReturn(Optional.empty());

        CreatePostRequest request = CreatePostRequest.builder().skillId(5L).content("hello").build();

        assertThatThrownBy(() -> postService.createPost(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Skill not found");

        verify(postRepository, never()).save(any());
    }

    @Test
    void createPost_shouldSavePostWithAuthorSkillAndTrimmedContent() {
        User author = User.builder().id(1L).build();
        Skill skill = Skill.builder().id(5L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(skillRepository.findById(5L)).thenReturn(Optional.of(skill));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(dtoMapper.toPostResponse(any(Post.class), anyLong(), anyLong(), anyBoolean()))
                .thenReturn(PostResponse.builder().build());

        postService.createPost(1L, CreatePostRequest.builder().skillId(5L).content("  Learning scales  ").build());

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthor()).isEqualTo(author);
        assertThat(captor.getValue().getSkill()).isEqualTo(skill);
        assertThat(captor.getValue().getContent()).isEqualTo("Learning scales");
    }

    @Test
    void getPost_shouldThrowResourceNotFoundException_whenMissing() {
        when(postRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");
    }

    @Test
    void getPost_shouldMapCountsAndLikedByMe() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();

        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.countByPostId(100L)).thenReturn(4L);
        when(postCommentRepository.countByPostId(100L)).thenReturn(2L);
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);
        when(dtoMapper.toPostResponse(post, 4L, 2L, true)).thenReturn(PostResponse.builder().id(100L).build());

        assertThat(postService.getPost(1L, 100L).getId()).isEqualTo(100L);
    }

    @Test
    void deletePost_shouldThrowResourceNotFoundException_whenNotAuthor() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");

        verify(postRepository, never()).delete(any());
    }

    @Test
    void deletePost_shouldRemoveLikesAndCommentsBeforePost() {
        Post post = Post.builder().id(100L).author(User.builder().id(1L).build()).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        postService.deletePost(1L, 100L);

        var order = inOrder(postLikeRepository, postCommentRepository, postRepository);
        order.verify(postLikeRepository).deleteByPostId(100L);
        order.verify(postCommentRepository).deleteByPostId(100L);
        order.verify(postRepository).delete(post);
    }

    @Test
    void getFeed_shouldIncludeFollowedUsersAndSelf() {
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(List.of(2L, 3L));
        when(postRepository.findByAuthorIdInOrderByCreatedAtDescIdDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        postService.getFeed(1L, null, 0, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(postRepository).findByAuthorIdInOrderByCreatedAtDescIdDesc(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void getFeed_shouldFilterBySkill_whenSkillIdGiven() {
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(List.of());
        when(postRepository.findByAuthorIdInAndSkillIdOrderByCreatedAtDescIdDesc(any(), eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(postService.getFeed(1L, 7L, 0, 20)).isEmpty();

        verify(postRepository, never()).findByAuthorIdInOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void getExplorePosts_shouldThrowBadRequestException_whenSizeAboveMax() {
        assertThatThrownBy(() -> postService.getExplorePosts(1L, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getExplorePosts_shouldThrowBadRequestException_whenPageNegative() {
        assertThatThrownBy(() -> postService.getExplorePosts(1L, null, -1, 20))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getUserPosts_shouldThrowResourceNotFoundException_whenAuthorMissing() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> postService.getUserPosts(1L, 9L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void likePost_shouldThrowAlreadyExistsException_whenAlreadyLiked() {
        Post post = Post.builder().id(100L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> postService.likePost(1L, 100L))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("You have already liked this post");

        verify(postLikeRepository, never()).save(any());
    }

    @Test
    void likePost_shouldSaveLike() {
        User user = User.builder().id(1L).build();
        Post post = Post.builder().id(100L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(false, true);
        when(dtoMapper.toPostResponse(any(Post.class), anyLong(), anyLong(), anyBoolean()))
                .thenReturn(PostResponse.builder().build());

        postService.likePost(1L, 100L);

        ArgumentCaptor<PostLike> captor = ArgumentCaptor.forClass(PostLike.class);
        verify(postLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getPost()).isEqualTo(post);
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void unlikePost_shouldThrowResourceNotFoundException_whenNotLiked() {
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.findByPostIdAndUserId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.unlikePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("You have not liked this post");

        verify(postLikeRepository, never()).delete(any());
    }

    @Test
    void addComment_shouldSaveTrimmedCommentForPost() {
        User author = User.builder().id(1L).build();
        Post post = Post.builder().id(100L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postCommentRepository.save(any(PostComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.addComment(1L, 100L, CreateCommentRequest.builder().content(" nice ").build());

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getPost()).isEqualTo(post);
        assertThat(captor.getValue().getAuthor()).isEqualTo(author);
        assertThat(captor.getValue().getContent()).isEqualTo("nice");
    }

    @Test
    void deleteComment_shouldThrowResourceNotFoundException_whenNotCommentAuthor() {
        PostComment comment = PostComment.builder().id(5L).author(User.builder().id(2L).build()).build();
        when(postCommentRepository.findByIdAndPostId(5L, 100L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> postService.deleteComment(1L, 100L, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Comment not found");

        verify(postCommentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_shouldDeleteComment_whenAuthor() {
        PostComment comment = PostComment.builder().id(5L).author(User.builder().id(1L).build()).build();
        when(postCommentRepository.findByIdAndPostId(5L, 100L)).thenReturn(Optional.of(comment));

        postService.deleteComment(1L, 100L, 5L);

        verify(postCommentRepository).delete(comment);
    }
}
