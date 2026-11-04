package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.MatchResponse;
import com.sashank.paraspa.dto.response.PostResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.service.FeedService;
import com.sashank.paraspa.service.PeopleSuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock private FeedService feedService;
    @Mock private PeopleSuggestionService peopleSuggestionService;

    @InjectMocks
    private FeedController feedController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(feedController).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void getFeed_defaultsToTwentyAndReturnsTheCursorEnvelope() throws Exception {
        when(feedService.getFeed(anyLong(), any(), anyInt())).thenReturn(
                new CursorPageResponse<>(List.of(PostResponse.builder().id(9L).build()), "next"));

        mvc.perform(get("/api/feed").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(9))
                .andExpect(jsonPath("$.nextCursor").value("next"));

        verify(feedService).getFeed(1L, null, 20);
    }

    @Test
    void getFeed_passesCursorAndSize_andSurfacesABadCursorAs400() throws Exception {
        when(feedService.getFeed(1L, "abc", 5)).thenReturn(new CursorPageResponse<>(List.of(), null));
        when(feedService.getFeed(1L, "bad", 20)).thenThrow(new BadRequestException("Invalid cursor"));

        mvc.perform(get("/api/feed").requestAttr("userId", 1L).param("cursor", "abc").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/feed").requestAttr("userId", 1L).param("cursor", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid cursor"));
    }

    @Test
    void getExplore_passesSkillIdCursorAndSize() throws Exception {
        when(feedService.getExplore(anyLong(), any(), any(), anyInt())).thenReturn(new CursorPageResponse<>(List.of(), null));

        mvc.perform(get("/api/explore").requestAttr("userId", 1L)).andExpect(status().isOk());
        mvc.perform(get("/api/explore").requestAttr("userId", 1L)
                .param("skillId", "7").param("cursor", "c").param("size", "10")).andExpect(status().isOk());

        verify(feedService).getExplore(1L, null, null, 20);
        verify(feedService).getExplore(1L, 7L, "c", 10);
    }

    @Test
    void getExplore_returns400_whenSkillIdIsNotANumber() throws Exception {
        mvc.perform(get("/api/explore").requestAttr("userId", 1L).param("skillId", "guitar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'skillId'"));
    }

    @Test
    void getSuggestedPeople_defaultsToFirstPageOfTwenty_andUsesTheMatchShape() throws Exception {
        when(peopleSuggestionService.getSuggestedPeople(anyLong(), anyInt(), anyInt())).thenReturn(List.of(
                MatchResponse.builder().user(UserResponse.builder().id(4L).name("Ravi").build()).isMutual(true).build()));

        mvc.perform(get("/api/explore/people").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.name").value("Ravi"))
                .andExpect(jsonPath("$[0].isMutual").value(true))
                .andExpect(jsonPath("$[0].user.email").value((Object) null));

        verify(peopleSuggestionService).getSuggestedPeople(1L, 0, 20);
    }

    @Test
    void getSuggestedPeople_passesPageAndSize_andRejectsBadValuesWith400() throws Exception {
        when(peopleSuggestionService.getSuggestedPeople(1L, 2, 50)).thenReturn(List.of());
        when(peopleSuggestionService.getSuggestedPeople(1L, 0, 500))
                .thenThrow(new BadRequestException("page must be >= 0 and size must be between 1 and 100"));

        mvc.perform(get("/api/explore/people").requestAttr("userId", 1L).param("page", "2").param("size", "50"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/explore/people").requestAttr("userId", 1L).param("size", "500"))
                .andExpect(status().isBadRequest());
    }
}
