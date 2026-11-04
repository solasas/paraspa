package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.PostMedia;
import com.sashank.paraspa.entity.Skill;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.FollowRepository;
import com.sashank.paraspa.repository.CommentRepository;
import com.sashank.paraspa.repository.PostLikeRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.SkillRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.storage.StorageService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private FollowRepository followRepository;
    @Mock private UserRepository userRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private StorageService storageService;
    @Mock private DtoMapper dtoMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PostServiceImpl postService;

    private final User author = User.builder().id(1L).build();

    private static MultipartFile image(String name, byte[] bytes) {
        return new MockMultipartFile("images", name, "image/png", bytes);
    }

    private void stubAuthor() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
    }

    /** Lets createPost run to the end: persists with an id and maps to a response. */
    private void stubSaveAndMap() {
        when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(dtoMapper.toPostResponse(any(Post.class), anyBoolean())).thenReturn(PostResponse.builder().id(100L).build());
    }

    private ArgumentCaptor<Post> savedPost() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).saveAndFlush(captor.capture());
        return captor;
    }

    // =============================================================== creation validation

    @Test
    void createPost_shouldRequireTextOrAnImage() {
        stubAuthor();

        assertThatThrownBy(() -> postService.createPost(1L, null, List.of(), List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A post needs text or at least one image");

        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldTreatBlankTextAsMissing() {
        stubAuthor();

        assertThatThrownBy(() -> postService.createPost(1L, "   \n ", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A post needs text or at least one image");
    }

    @Test
    void createPost_shouldTreatEmptyFilePartsAsMissing() {
        stubAuthor();
        MultipartFile emptyPart = new MockMultipartFile("images", "", "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> postService.createPost(1L, null, null, List.of(emptyPart)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A post needs text or at least one image");
    }

    @Test
    void createPost_shouldRejectTextOverTwoThousandCharacters() {
        stubAuthor();

        assertThatThrownBy(() -> postService.createPost(1L, "x".repeat(2001), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2000");

        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldAcceptExactlyTwoThousandCharacters() {
        stubAuthor();
        stubSaveAndMap();

        postService.createPost(1L, "x".repeat(2000), null, null);

        assertThat(savedPost().getValue().getContent()).hasSize(2000);
    }

    @Test
    void createPost_shouldRejectMoreThanFourImagesAndStoreNothing() {
        stubAuthor();
        List<MultipartFile> five = IntStream.range(0, 5).mapToObj(i -> image("p" + i + ".png", PNG)).toList();

        assertThatThrownBy(() -> postService.createPost(1L, "hi", null, five))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A post can have at most 4 images");

        verifyNoInteractions(storageService);
        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldAcceptExactlyFourImages() {
        stubAuthor();
        stubSaveAndMap();
        when(storageService.store(eq("posts"), anyString(), any())).thenReturn("/files/posts/a.png");
        List<MultipartFile> four = IntStream.range(0, 4).mapToObj(i -> image("p" + i + ".png", PNG)).toList();

        postService.createPost(1L, null, null, four);

        assertThat(savedPost().getValue().getMedia()).hasSize(4);
    }

    @Test
    void createPost_shouldRejectMoreThanThreeSkillTags() {
        stubAuthor();

        assertThatThrownBy(() -> postService.createPost(1L, "hi", List.of(1L, 2L, 3L, 4L), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A post can have at most 3 skill tags");

        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldIgnoreDuplicateSkillTags() {
        stubAuthor();
        stubSaveAndMap();
        Skill s1 = Skill.builder().id(1L).build();
        Skill s2 = Skill.builder().id(2L).build();
        Skill s3 = Skill.builder().id(3L).build();
        when(skillRepository.findById(1L)).thenReturn(Optional.of(s1));
        when(skillRepository.findById(2L)).thenReturn(Optional.of(s2));
        when(skillRepository.findById(3L)).thenReturn(Optional.of(s3));

        // five ids but only three distinct skills: allowed, tags keep first-seen order
        postService.createPost(1L, "hi", List.of(2L, 2L, 1L, 3L, 1L), null);

        assertThat(savedPost().getValue().getSkills()).containsExactly(s2, s1, s3);
    }

    @Test
    void createPost_shouldThrowResourceNotFoundException_whenSkillMissing() {
        stubAuthor();
        when(skillRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(1L, "hi", List.of(9L), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Skill not found");

        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldRejectAnImageThatIsNotJpgPngOrWebpEvenIfNamedPng() {
        stubAuthor();
        MultipartFile fake = new MockMultipartFile("images", "evil.png", "image/png", "<html></html>".getBytes());

        assertThatThrownBy(() -> postService.createPost(1L, "hi", null, List.of(fake)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Images must be JPG, PNG or WebP");

        verifyNoInteractions(storageService);
    }

    @Test
    void createPost_shouldRejectAnImageOverTwoMegabytes() {
        stubAuthor();
        byte[] big = new byte[(int) PostServiceImpl.MAX_IMAGE_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertThatThrownBy(() -> postService.createPost(1L, "hi", null, List.of(image("big.png", big))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Each image must be at most 2 MB");

        verifyNoInteractions(storageService);
    }

    @Test
    void createPost_shouldAcceptAnImageOfExactlyTwoMegabytes() {
        stubAuthor();
        stubSaveAndMap();
        when(storageService.store(eq("posts"), anyString(), any())).thenReturn("/files/posts/a.png");
        byte[] exactly = new byte[(int) PostServiceImpl.MAX_IMAGE_BYTES];
        System.arraycopy(PNG, 0, exactly, 0, PNG.length);

        postService.createPost(1L, null, null, List.of(image("ok.png", exactly)));

        verify(storageService).store(eq("posts"), anyString(), any());
    }

    @Test
    void createPost_shouldStoreNothing_whenOneOfSeveralImagesIsInvalid() {
        stubAuthor();
        List<MultipartFile> files = List.of(image("good.png", PNG), image("bad.png", "nope".getBytes()));

        assertThatThrownBy(() -> postService.createPost(1L, null, null, files))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(storageService);
        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(1L, "hi", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    // ============================================================== creation behaviour

    @Test
    void createPost_shouldSaveTrimmedTextWithAuthorAndNoMedia() {
        stubAuthor();
        stubSaveAndMap();

        postService.createPost(1L, "  Learning scales  ", null, null);

        Post post = savedPost().getValue();
        assertThat(post.getAuthor()).isEqualTo(author);
        assertThat(post.getContent()).isEqualTo("Learning scales");
        assertThat(post.getMedia()).isEmpty();
        assertThat(post.getLikeCount()).isZero();
        assertThat(post.getCommentCount()).isZero();
        verifyNoInteractions(storageService);
    }

    @Test
    void createPost_shouldAllowImageOnlyPostWithNullContent() {
        stubAuthor();
        stubSaveAndMap();
        when(storageService.store(eq("posts"), anyString(), any())).thenReturn("/files/posts/a.png");

        postService.createPost(1L, "   ", null, List.of(image("a.png", PNG)));

        assertThat(savedPost().getValue().getContent()).isNull();
    }

    @Test
    void createPost_shouldStoreImagesInOrderAndLinkMediaRowsByPosition() {
        stubAuthor();
        stubSaveAndMap();
        when(storageService.store(eq("posts"), anyString(), any()))
                .thenReturn("/files/posts/first.png", "/files/posts/second.jpg");

        postService.createPost(1L, null, null,
                List.of(image("a.png", PNG), new MockMultipartFile("images", "b.jpg", "image/jpeg", JPEG)));

        Post post = savedPost().getValue();
        assertThat(post.getMedia()).extracting(PostMedia::getUrl)
                .containsExactly("/files/posts/first.png", "/files/posts/second.jpg");
        assertThat(post.getMedia()).extracting(PostMedia::getPosition).containsExactly(0, 1);
        assertThat(post.getMedia()).allSatisfy(m -> assertThat(m.getPost()).isSameAs(post));

        // file names are user-scoped, random, and carry the extension of the *detected* type
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        verify(storageService, org.mockito.Mockito.times(2)).store(eq("posts"), names.capture(), any());
        assertThat(names.getAllValues().get(0)).matches("1-[0-9a-f-]{36}\\.png");
        assertThat(names.getAllValues().get(1)).matches("1-[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void createPost_shouldDeleteAlreadyStoredFiles_whenStoringALaterImageFails() {
        stubAuthor();
        when(storageService.store(eq("posts"), anyString(), any()))
                .thenReturn("/files/posts/first.png")
                .thenThrow(new IllegalStateException("disk full"));

        assertThatThrownBy(() -> postService.createPost(1L, null, null,
                List.of(image("a.png", PNG), image("b.png", PNG))))
                .isInstanceOf(IllegalStateException.class);

        verify(storageService).delete("/files/posts/first.png");
        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void createPost_shouldDeleteStoredFiles_whenSavingThePostFails() {
        stubAuthor();
        when(storageService.store(eq("posts"), anyString(), any())).thenReturn("/files/posts/a.png");
        when(postRepository.saveAndFlush(any(Post.class))).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> postService.createPost(1L, null, null, List.of(image("a.png", PNG))))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(storageService).delete("/files/posts/a.png");
    }

    // ===================================================================== deletion

    @Test
    void deletePost_shouldThrowResourceNotFoundException_whenNotTheAuthor() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");

        verify(postRepository, never()).delete(any());
        verifyNoInteractions(storageService);
    }

    @Test
    void deletePost_shouldThrowResourceNotFoundException_whenPostMissing() {
        when(postRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(postRepository, never()).delete(any());
    }

    @Test
    void deletePost_shouldRemoveLikesCommentsAndPostThenTheImageFiles() {
        Post post = Post.builder().id(100L).author(author).build();
        post.getMedia().add(PostMedia.builder().post(post).url("/files/posts/a.png").position(0).build());
        post.getMedia().add(PostMedia.builder().post(post).url("/files/posts/b.png").position(1).build());
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        postService.deletePost(1L, 100L);

        InOrder order = inOrder(postLikeRepository, commentRepository, postRepository, storageService);
        order.verify(postLikeRepository).deleteByPostId(100L);
        order.verify(commentRepository).deleteByPostId(100L);
        order.verify(postRepository).delete(post);     // media rows go with it via cascade
        order.verify(storageService).delete("/files/posts/a.png");
        order.verify(storageService).delete("/files/posts/b.png");
    }

    // ============================================================= cursor pagination

    private static Post post(long id, LocalDateTime createdAt) {
        return Post.builder().id(id).author(User.builder().id(2L).build()).createdAt(createdAt).build();
    }

    private void stubMapping() {
        when(postLikeRepository.findLikedPostIds(any(), anyCollection())).thenReturn(List.of());
        when(dtoMapper.toPostResponse(any(Post.class), anyBoolean())).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            return PostResponse.builder().id(p.getId()).createdAt(p.getCreatedAt()).build();
        });
    }

    @Test
    void getUserPosts_firstPageShouldFetchOneExtraRowAndReturnNewestFirst() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0);
        when(userRepository.existsById(2L)).thenReturn(true);
        // repository contract: newest first; size 2 => fetch 3
        when(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(post(30, t.plusMinutes(3)), post(20, t.plusMinutes(2)), post(10, t.plusMinutes(1))));
        stubMapping();

        CursorPageResponse<PostResponse> page = postService.getUserPosts(1L, 2L, null, 2);

        assertThat(page.getItems()).extracting(PostResponse::getId).containsExactly(30L, 20L);
        assertThat(page.getNextCursor()).isNotNull();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    void getUserPosts_nextCursorShouldPointAtTheLastReturnedPost() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0, 0, 123_456_000);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(post(30, t.plusMinutes(3)), post(20, t.plusMinutes(2)), post(10, t.plusMinutes(1))));
        stubMapping();

        CursorUtil.Keyset keyset = CursorUtil.decodeKeyset(postService.getUserPosts(1L, 2L, null, 2).getNextCursor());

        // post 20 is the last one on the page; the dropped extra row (10) must not leak into the cursor
        assertThat(keyset.id()).isEqualTo(20L);
        assertThat(keyset.createdAt()).isEqualTo(t.plusMinutes(2));
    }

    @Test
    void getUserPosts_lastPageShouldHaveNoNextCursor() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(post(20, t.plusMinutes(2)), post(10, t.plusMinutes(1))));
        stubMapping();

        CursorPageResponse<PostResponse> page = postService.getUserPosts(1L, 2L, null, 2);

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getNextCursor()).isNull();
    }

    @Test
    void getUserPosts_shouldContinueAfterTheCursorUsingBothCreatedAtAndId() {
        // two posts share a timestamp: only (createdAt, id) together identify the position
        LocalDateTime same = LocalDateTime.of(2026, 11, 4, 12, 0, 0, 500_000_000);
        String cursor = CursorUtil.encode(same, 21L);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(postRepository.findByAuthorIdBefore(eq(2L), eq(same), eq(21L), any(Pageable.class)))
                .thenReturn(List.of(post(20, same)));
        stubMapping();

        CursorPageResponse<PostResponse> page = postService.getUserPosts(1L, 2L, cursor, 5);

        assertThat(page.getItems()).extracting(PostResponse::getId).containsExactly(20L);
        verify(postRepository, never()).findByAuthorIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void getUserPosts_pagingThroughEveryPostShouldVisitEachOnceInOrder() {
        // simulate the database: 5 posts, two of them with the same timestamp, newest first
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0);
        List<Post> all = List.of(post(50, t.plusMinutes(4)), post(40, t.plusMinutes(3)), post(30, t.plusMinutes(3)),
                post(20, t.plusMinutes(2)), post(10, t.plusMinutes(1)));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenAnswer(invocation -> all.stream().limit(((Pageable) invocation.getArgument(1)).getPageSize()).toList());
        when(postRepository.findByAuthorIdBefore(eq(2L), any(LocalDateTime.class), any(Long.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    LocalDateTime at = invocation.getArgument(1);
                    long id = invocation.getArgument(2);
                    return all.stream()
                            .filter(p -> p.getCreatedAt().isBefore(at) || (p.getCreatedAt().equals(at) && p.getId() < id))
                            .limit(((Pageable) invocation.getArgument(3)).getPageSize()).toList();
                });
        stubMapping();

        List<Long> seen = new java.util.ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            CursorPageResponse<PostResponse> page = postService.getUserPosts(1L, 2L, cursor, 2);
            page.getItems().forEach(p -> seen.add(p.getId()));
            cursor = page.getNextCursor();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(seen).containsExactly(50L, 40L, 30L, 20L, 10L);   // newest first, no gaps, no repeats
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void getUserPosts_shouldReturnEmptyPageForAnAuthorWithNoPosts() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class))).thenReturn(List.of());

        CursorPageResponse<PostResponse> page = postService.getUserPosts(1L, 2L, null, 20);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
        verifyNoInteractions(dtoMapper);
    }

    @Test
    void getUserPosts_shouldRejectMalformedCursorAndBadSizes() {
        assertThatThrownBy(() -> postService.getUserPosts(1L, 2L, "not-a-cursor!", 20))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> postService.getUserPosts(1L, 2L, null, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> postService.getUserPosts(1L, 2L, null, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getUserPosts_shouldThrowResourceNotFoundException_whenAuthorMissing() {
        when(userRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> postService.getUserPosts(1L, 9L, null, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void getUserPosts_shouldMarkLikedPostsWithOneQueryForThePage() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 4, 12, 0);
        Post liked = post(20, t.plusMinutes(2));
        Post notLiked = post(10, t.plusMinutes(1));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(eq(2L), any(Pageable.class)))
                .thenReturn(List.of(liked, notLiked));
        when(postLikeRepository.findLikedPostIds(eq(1L), anyCollection())).thenReturn(List.of(20L));
        when(dtoMapper.toPostResponse(liked, true)).thenReturn(PostResponse.builder().id(20L).likedByMe(true).build());
        when(dtoMapper.toPostResponse(notLiked, false)).thenReturn(PostResponse.builder().id(10L).build());

        CursorPageResponse<PostResponse> page = postService.getUserPosts(1L, 2L, null, 20);

        assertThat(page.getItems()).extracting(PostResponse::isLikedByMe).containsExactly(true, false);
        verify(postLikeRepository, org.mockito.Mockito.times(1)).findLikedPostIds(any(), anyCollection());
        verify(postLikeRepository, never()).existsByPostIdAndUserId(any(), any());
    }

    // ================================================================ feeds (page/size)

    @Test
    void getFeed_shouldIncludeFollowedUsersAndSelf() {
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(List.of(2L, 3L));
        when(postRepository.findByAuthorIdInOrderByCreatedAtDescIdDesc(any(), any(Pageable.class))).thenReturn(List.of());

        postService.getFeed(1L, null, 0, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(postRepository).findByAuthorIdInOrderByCreatedAtDescIdDesc(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void getFeed_shouldFilterBySkillTag_whenSkillIdGiven() {
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(List.of());
        when(postRepository.findByAuthorIdInAndSkills_IdOrderByCreatedAtDescIdDesc(any(), eq(7L), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(postService.getFeed(1L, 7L, 0, 20)).isEmpty();

        verify(postRepository, never()).findByAuthorIdInOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void getExplorePosts_shouldFilterBySkillTag_whenSkillIdGiven() {
        when(postRepository.findBySkills_IdOrderByCreatedAtDescIdDesc(eq(7L), any(Pageable.class))).thenReturn(List.of());

        assertThat(postService.getExplorePosts(1L, 7L, 0, 20)).isEmpty();

        verify(postRepository, never()).findAllByOrderByCreatedAtDescIdDesc(any());
    }

    @Test
    void feedLists_shouldRejectInvalidPaging() {
        assertThatThrownBy(() -> postService.getExplorePosts(1L, null, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> postService.getExplorePosts(1L, null, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> postService.getFeed(1L, null, 0, 0)).isInstanceOf(BadRequestException.class);
    }

    // ======================================================================== likes

    @Test
    void likePost_shouldInsertTheLikeThenIncrementTheCounter_whenNewLike() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();
        when(userRepository.existsById(1L)).thenReturn(true);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.insertIfAbsent(eq(100L), eq(1L), any(LocalDateTime.class))).thenReturn(1);
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);
        when(dtoMapper.toPostResponse(post, true)).thenReturn(PostResponse.builder().id(100L).likedByMe(true).build());

        PostResponse response = postService.likePost(1L, 100L);

        InOrder order = inOrder(postLikeRepository, postRepository);
        order.verify(postLikeRepository).insertIfAbsent(eq(100L), eq(1L), any(LocalDateTime.class));
        order.verify(postRepository).incrementLikeCount(100L);
        assertThat(response.isLikedByMe()).isTrue();
    }

    @Test
    void likePost_shouldBeIdempotent_whenAlreadyLiked() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).likeCount(5).build();
        when(userRepository.existsById(1L)).thenReturn(true);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        // the row already exists, so the insert reports 0 rows
        when(postLikeRepository.insertIfAbsent(eq(100L), eq(1L), any(LocalDateTime.class))).thenReturn(0);
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);
        when(dtoMapper.toPostResponse(post, true)).thenReturn(PostResponse.builder().likeCount(5).likedByMe(true).build());

        PostResponse response = postService.likePost(1L, 100L);   // no exception: a repeat is not an error

        verify(postRepository, never()).incrementLikeCount(any());   // the counter must not move
        assertThat(response.getLikeCount()).isEqualTo(5);
        assertThat(response.isLikedByMe()).isTrue();
    }

    @Test
    void likePost_calledTwiceInARow_shouldIncrementOnlyOnce() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();
        when(userRepository.existsById(1L)).thenReturn(true);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.insertIfAbsent(eq(100L), eq(1L), any(LocalDateTime.class))).thenReturn(1, 0);
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);
        when(dtoMapper.toPostResponse(any(Post.class), anyBoolean())).thenReturn(PostResponse.builder().build());

        postService.likePost(1L, 100L);
        postService.likePost(1L, 100L);

        verify(postRepository, org.mockito.Mockito.times(1)).incrementLikeCount(100L);
    }

    @Test
    void likePost_shouldThrowResourceNotFoundException_whenPostMissing() {
        when(userRepository.existsById(1L)).thenReturn(true);
        when(postRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.likePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");

        verify(postLikeRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void likePost_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> postService.likePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");

        verify(postLikeRepository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void unlikePost_shouldDeleteTheLikeThenDecrementTheCounter_whenLiked() {
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.deleteLike(100L, 1L)).thenReturn(1);

        postService.unlikePost(1L, 100L);

        InOrder order = inOrder(postLikeRepository, postRepository);
        order.verify(postLikeRepository).deleteLike(100L, 1L);
        order.verify(postRepository).decrementLikeCount(100L);
    }

    @Test
    void unlikePost_shouldBeIdempotent_whenNotLiked() {
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.deleteLike(100L, 1L)).thenReturn(0);

        postService.unlikePost(1L, 100L);   // no exception

        verify(postRepository, never()).decrementLikeCount(any());
    }

    @Test
    void unlikePost_calledTwiceInARow_shouldDecrementOnlyOnce() {
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.deleteLike(100L, 1L)).thenReturn(1, 0);

        postService.unlikePost(1L, 100L);
        postService.unlikePost(1L, 100L);

        verify(postRepository, org.mockito.Mockito.times(1)).decrementLikeCount(100L);
    }

    @Test
    void unlikePost_shouldThrowResourceNotFoundException_whenPostMissing() {
        when(postRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.unlikePost(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");

        verify(postLikeRepository, never()).deleteLike(any(), any());
    }

    @Test
    void getLikes_shouldReturnPublicUsersForTheRequestedPage() {
        User liker = User.builder().id(5L).email("liker@example.com").build();
        com.sashank.paraspa.dto.response.UserResponse mapped =
                com.sashank.paraspa.dto.response.UserResponse.builder().id(5L).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.findByPostIdOrderByCreatedAtDescIdDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of(com.sashank.paraspa.entity.PostLike.builder().user(liker).build()));
        when(dtoMapper.toPublicUserResponse(liker)).thenReturn(mapped);

        assertThat(postService.getLikes(100L, 2, 30)).containsExactly(mapped);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postLikeRepository).findByPostIdOrderByCreatedAtDescIdDesc(eq(100L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
        verify(dtoMapper, never()).toUserResponse(any());   // the variant that carries the email is never used
    }

    @Test
    void getLikes_shouldReturnEmptyListWhenNobodyLiked() {
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.findByPostIdOrderByCreatedAtDescIdDesc(eq(100L), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(postService.getLikes(100L, 0, 20)).isEmpty();
    }

    @Test
    void getLikes_shouldRejectInvalidPagingAndMissingPost() {
        assertThatThrownBy(() -> postService.getLikes(100L, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> postService.getLikes(100L, -1, 20)).isInstanceOf(BadRequestException.class);

        when(postRepository.findById(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> postService.getLikes(100L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Post not found");
    }

    @Test
    void getPost_shouldReportLikedByMeForTheViewer() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);
        when(dtoMapper.toPostResponse(post, true)).thenReturn(PostResponse.builder().likedByMe(true).build());

        assertThat(postService.getPost(1L, 100L).isLikedByMe()).isTrue();
    }


    // ------------------------------------------------------------ events

    @Test
    void likePost_shouldPublishALikedEvent_onlyWhenTheLikeWasReallyAdded() {
        Post post = Post.builder().id(100L).author(User.builder().id(2L).build()).build();
        when(userRepository.existsById(1L)).thenReturn(true);
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postLikeRepository.insertIfAbsent(eq(100L), eq(1L), any(LocalDateTime.class))).thenReturn(1, 0);
        when(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).thenReturn(true);
        when(dtoMapper.toPostResponse(any(Post.class), anyBoolean())).thenReturn(PostResponse.builder().build());

        postService.likePost(1L, 100L);   // new like
        postService.likePost(1L, 100L);   // repeat: nothing new happened

        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(any(Object.class));
        verify(eventPublisher).publishEvent(new com.sashank.paraspa.event.PostLikedEvent(100L, 2L, 1L));
    }

    @Test
    void unlikePost_shouldPublishNothing() {
        when(postRepository.findById(100L)).thenReturn(Optional.of(Post.builder().id(100L).build()));
        when(postLikeRepository.deleteLike(100L, 1L)).thenReturn(1);

        postService.unlikePost(1L, 100L);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
