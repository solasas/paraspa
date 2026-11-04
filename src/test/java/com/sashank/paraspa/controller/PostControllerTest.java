package com.sashank.paraspa.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.PostMediaResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.dto.response.SkillResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Mock
    private PostService postService;

    @InjectMocks
    private PostController postController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(postController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ------------------------------------------------------------------- create

    @Test
    void createPost_json_shouldPassContentAndSkillIdsAndReturn201() throws Exception {
        when(postService.createPost(eq(1L), any(), any(), any())).thenReturn(PostResponse.builder().id(100L).build());

        mvc.perform(post("/api/posts").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\",\"skillIds\":[1,2]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100));

        verify(postService).createPost(1L, "hello", List.of(1L, 2L), null);
    }

    @Test
    void createPost_json_shouldReturn400_whenContentOver2000Characters() throws Exception {
        mvc.perform(post("/api/posts").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2000")));
    }

    @Test
    void createPost_json_shouldLeaveTheSkillTagLimitToTheService() throws Exception {
        when(postService.createPost(eq(1L), any(), any(), any()))
                .thenThrow(new BadRequestException("A post can have at most 3 skill tags"));

        // five ids with duplicates reach the service untouched; it decides what counts as "distinct"
        mvc.perform(post("/api/posts").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\",\"skillIds\":[1,2,3,4,1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A post can have at most 3 skill tags"));

        verify(postService).createPost(1L, "hi", List.of(1L, 2L, 3L, 4L, 1L), null);
    }

    @Test
    void createPost_json_shouldSurfaceTheServicesNeedTextOrImageRule() throws Exception {
        when(postService.createPost(eq(1L), any(), any(), any()))
                .thenThrow(new BadRequestException("A post needs text or at least one image"));

        mvc.perform(post("/api/posts").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A post needs text or at least one image"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPost_multipart_shouldBindRepeatedImagesAndSkillIds() throws Exception {
        when(postService.createPost(eq(1L), any(), any(), any())).thenReturn(PostResponse.builder().id(100L).build());

        mvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .file(new MockMultipartFile("images", "b.png", "image/png", PNG))
                        .param("content", "two pictures")
                        .param("skillIds", "5")
                        .param("skillIds", "6")
                        .requestAttr("userId", 1L))
                .andExpect(status().isCreated());

        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.forClass(List.class);
        verify(postService).createPost(eq(1L), eq("two pictures"), eq(List.of(5L, 6L)), files.capture());
        assertThat(files.getValue()).extracting(MultipartFile::getOriginalFilename).containsExactly("a.png", "b.png");
    }

    @Test
    void createPost_multipart_shouldAllowImagesOnly() throws Exception {
        when(postService.createPost(eq(1L), any(), any(), any())).thenReturn(PostResponse.builder().build());

        mvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("images", "a.png", "image/png", PNG))
                        .requestAttr("userId", 1L))
                .andExpect(status().isCreated());

        verify(postService).createPost(eq(1L), eq(null), eq(null), any());
    }

    @Test
    void createPost_multipart_shouldPassNullImagesWhenNoFileIsSent() throws Exception {
        when(postService.createPost(eq(1L), any(), any(), any())).thenReturn(PostResponse.builder().build());

        mvc.perform(multipart("/api/posts").param("content", "text only").requestAttr("userId", 1L))
                .andExpect(status().isCreated());

        verify(postService).createPost(1L, "text only", null, null);
    }

    @Test
    void createPost_shouldReturn415_forOtherContentTypes() throws Exception {
        mvc.perform(post("/api/posts").requestAttr("userId", 1L)
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // -------------------------------------------------------------------- read

    @Test
    void getPost_shouldReturn404_whenMissing() throws Exception {
        when(postService.getPost(1L, 99L)).thenThrow(new ResourceNotFoundException("Post not found"));

        mvc.perform(get("/api/posts/99").requestAttr("userId", 1L)).andExpect(status().isNotFound());
    }

    @Test
    void postResponseJson_shouldExposeSkillsMediaAndCountersAndAllowNullContent() throws Exception {
        PostResponse response = PostResponse.builder().id(100L).content(null)
                .skills(List.of(SkillResponse.builder().id(1L).name("Guitar").build()))
                .media(List.of(PostMediaResponse.builder().url("/files/posts/a.png").position(0).build()))
                .likeCount(4).commentCount(2).likedByMe(true).build();
        when(postService.getPost(1L, 100L)).thenReturn(response);

        mvc.perform(get("/api/posts/100").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value((Object) null))
                .andExpect(jsonPath("$.skills[0].name").value("Guitar"))
                .andExpect(jsonPath("$.media[0].url").value("/files/posts/a.png"))
                .andExpect(jsonPath("$.media[0].position").value(0))
                .andExpect(jsonPath("$.likeCount").value(4))
                .andExpect(jsonPath("$.commentCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(true))
                .andExpect(jsonPath("$.skill").doesNotExist());   // the old single-skill field is gone
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deletePost_shouldReturn204() throws Exception {
        mvc.perform(delete("/api/posts/100").requestAttr("userId", 1L)).andExpect(status().isNoContent());

        verify(postService).deletePost(1L, 100L);
    }

    @Test
    void deletePost_shouldReturn404_whenNotTheAuthor() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Post not found"))
                .when(postService).deletePost(1L, 100L);

        mvc.perform(delete("/api/posts/100").requestAttr("userId", 1L)).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------- user posts (cursor)

    @Test
    void getUserPosts_shouldDefaultToFirstPageOfTwentyAndReturnTheCursorEnvelope() throws Exception {
        when(postService.getUserPosts(anyLong(), anyLong(), any(), anyInt())).thenReturn(
                new CursorPageResponse<>(List.of(PostResponse.builder().id(30L).build()), "abc"));

        mvc.perform(get("/api/users/2/posts").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(30))
                .andExpect(jsonPath("$.nextCursor").value("abc"));

        verify(postService).getUserPosts(1L, 2L, null, 20);
    }

    @Test
    void getUserPosts_shouldPassCursorAndSizeThrough_andNullCursorOnTheLastPage() throws Exception {
        when(postService.getUserPosts(anyLong(), anyLong(), any(), anyInt())).thenReturn(
                new CursorPageResponse<>(List.of(), null));

        mvc.perform(get("/api/users/2/posts").requestAttr("userId", 1L).param("cursor", "abc").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value((Object) null));

        verify(postService).getUserPosts(1L, 2L, "abc", 5);
    }

    @Test
    void getUserPosts_shouldReturn400_forABadCursor() throws Exception {
        when(postService.getUserPosts(1L, 2L, "bad", 20)).thenThrow(new BadRequestException("Invalid cursor"));

        mvc.perform(get("/api/users/2/posts").requestAttr("userId", 1L).param("cursor", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid cursor"));
    }

    @Test
    void getUserPosts_shouldNoLongerAcceptPageParameterSemantics() throws Exception {
        when(postService.getUserPosts(anyLong(), anyLong(), any(), anyInt())).thenReturn(
                new CursorPageResponse<>(List.of(), null));

        // `page` is simply ignored now; the cursor is the only way to move through the list
        mvc.perform(get("/api/users/2/posts").requestAttr("userId", 1L).param("page", "3"))
                .andExpect(status().isOk());

        verify(postService).getUserPosts(1L, 2L, null, 20);
    }

    // ------------------------------------------------------------------- likes

    @Test
    void putLike_shouldReturn200WithTheUpdatedPost_notCreated() throws Exception {
        when(postService.likePost(1L, 100L))
                .thenReturn(PostResponse.builder().id(100L).likeCount(3).likedByMe(true).build());

        mvc.perform(put("/api/posts/100/like").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.likedByMe").value(true));
    }

    @Test
    void postLike_isNoLongerSupported() throws Exception {
        mvc.perform(post("/api/posts/100/like").requestAttr("userId", 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deleteLike_shouldReturn204WithNoBody() throws Exception {
        mvc.perform(delete("/api/posts/100/like").requestAttr("userId", 1L))
                .andExpect(status().isNoContent());

        verify(postService).unlikePost(1L, 100L);
    }

    @Test
    void putLike_shouldReturn404_whenPostMissing() throws Exception {
        when(postService.likePost(1L, 99L)).thenThrow(new ResourceNotFoundException("Post not found"));

        mvc.perform(put("/api/posts/99/like").requestAttr("userId", 1L)).andExpect(status().isNotFound());
    }

    @Test
    void getLikes_shouldDefaultToFirstPageOfTwenty_andNotExposeEmails() throws Exception {
        when(postService.getLikes(anyLong(), anyInt(), anyInt())).thenReturn(List.of(
                com.sashank.paraspa.dto.response.UserResponse.builder().id(5L).name("Ben").build()));

        mvc.perform(get("/api/posts/100/likes").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ben"))
                .andExpect(jsonPath("$[0].email").value((Object) null));

        verify(postService).getLikes(100L, 0, 20);
    }

    @Test
    void getLikes_shouldPassPageAndSize_andReturn400ForBadValues() throws Exception {
        when(postService.getLikes(100L, 2, 50)).thenReturn(List.of());
        when(postService.getLikes(100L, 0, 500)).thenThrow(new BadRequestException("page must be >= 0 and size must be between 1 and 100"));

        mvc.perform(get("/api/posts/100/likes").requestAttr("userId", 1L).param("page", "2").param("size", "50"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/100/likes").requestAttr("userId", 1L).param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oldCommentRoutesLeftOnPostController_areGone() throws Exception {
        // comments are handled by CommentController now; the old per-post delete URL no longer exists here
        assertThat(java.util.Arrays.stream(PostController.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("addComment", "getComments", "deleteComment");
    }

    @SuppressWarnings("unused")
    private JsonNode json(Object value) {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).valueToTree(value);
    }
}
