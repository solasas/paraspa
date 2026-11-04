package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.CreateCommentRequest;
import com.sashank.paraspa.dto.response.CommentResponse;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(commentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addComment_shouldReturn201_andPassContentAndParentId() throws Exception {
        when(commentService.addComment(eq(1L), eq(100L), any())).thenReturn(CommentResponse.builder().id(7L).parentId(5L).build());

        mvc.perform(post("/api/posts/100/comments").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"agreed\",\"parentId\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.parentId").value(5));

        ArgumentCaptor<CreateCommentRequest> captor = ArgumentCaptor.forClass(CreateCommentRequest.class);
        verify(commentService).addComment(eq(1L), eq(100L), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("agreed");
        assertThat(captor.getValue().getParentId()).isEqualTo(5L);
    }

    @Test
    void addComment_withoutParentId_shouldPassNull() throws Exception {
        when(commentService.addComment(eq(1L), eq(100L), any())).thenReturn(CommentResponse.builder().id(7L).build());

        mvc.perform(post("/api/posts/100/comments").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"top level\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCommentRequest> captor = ArgumentCaptor.forClass(CreateCommentRequest.class);
        verify(commentService).addComment(eq(1L), eq(100L), captor.capture());
        assertThat(captor.getValue().getParentId()).isNull();
    }

    @Test
    void addComment_shouldReturn400_whenContentOver500Characters() throws Exception {
        mvc.perform(post("/api/posts/100/comments").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("500")));
    }

    @Test
    void addComment_shouldReturn400_whenContentBlankOrMissing() throws Exception {
        mvc.perform(post("/api/posts/100/comments").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/posts/100/comments").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addComment_shouldSurfaceTheReplyDepthRuleAs400() throws Exception {
        when(commentService.addComment(eq(1L), eq(100L), any()))
                .thenThrow(new BadRequestException("Replies can only be one level deep: reply to the top-level comment"));

        mvc.perform(post("/api/posts/100/comments").requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\",\"parentId\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Replies can only be one level deep: reply to the top-level comment"));
    }

    @Test
    void getComments_shouldDefaultToTwentyAndReturnTheCursorEnvelopeWithNestedReplies() throws Exception {
        CommentResponse reply = CommentResponse.builder().id(11L).parentId(10L).content("reply").build();
        CommentResponse top = CommentResponse.builder().id(10L).content("top").replies(List.of(reply)).build();
        when(commentService.getComments(anyLong(), any(), anyInt())).thenReturn(new CursorPageResponse<>(List.of(top), "abc"));

        mvc.perform(get("/api/posts/100/comments").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].replies[0].id").value(11))
                .andExpect(jsonPath("$.items[0].replies[0].parentId").value(10))
                .andExpect(jsonPath("$.nextCursor").value("abc"));

        verify(commentService).getComments(100L, null, 20);
    }

    @Test
    void getComments_shouldPassCursorAndSize_andReturn400ForABadCursor() throws Exception {
        when(commentService.getComments(100L, "abc", 5)).thenReturn(new CursorPageResponse<>(List.of(), null));
        when(commentService.getComments(100L, "bad", 20)).thenThrow(new BadRequestException("Invalid cursor"));

        mvc.perform(get("/api/posts/100/comments").requestAttr("userId", 1L).param("cursor", "abc").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/posts/100/comments").requestAttr("userId", 1L).param("cursor", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid cursor"));
    }

    @Test
    void deleteComment_shouldBeServedAtApiCommentsId_with204() throws Exception {
        mvc.perform(delete("/api/comments/10").requestAttr("userId", 1L)).andExpect(status().isNoContent());

        verify(commentService).deleteComment(1L, 10L);
    }

    @Test
    void deleteComment_shouldReturn404_whenNotAllowedOrMissing() throws Exception {
        doThrow(new ResourceNotFoundException("Comment not found")).when(commentService).deleteComment(3L, 10L);

        mvc.perform(delete("/api/comments/10").requestAttr("userId", 3L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment not found"));
    }
}
