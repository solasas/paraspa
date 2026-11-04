package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.FollowResponse;
import com.sashank.paraspa.dto.response.FollowStatusResponse;
import com.sashank.paraspa.dto.response.PublicProfileResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ForbiddenException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.service.FollowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

    @Mock
    private FollowService followService;

    @InjectMocks
    private FollowController followController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(followController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void put_follow_shouldReturn200WithTheFollow() throws Exception {
        when(followService.followUser(1L, 2L)).thenReturn(FollowResponse.builder().id(10L).build());

        mvc.perform(put("/api/users/2/follow").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void post_follow_isNoLongerSupported() throws Exception {
        mvc.perform(post("/api/users/2/follow").requestAttr("userId", 1L))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void put_follow_shouldReturn400_forSelfFollow() throws Exception {
        when(followService.followUser(1L, 1L)).thenThrow(new BadRequestException("You cannot follow yourself"));

        mvc.perform(put("/api/users/1/follow").requestAttr("userId", 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot follow yourself"));
    }

    @Test
    void put_follow_shouldReturn404_forMissingUser() throws Exception {
        when(followService.followUser(1L, 99L)).thenThrow(new ResourceNotFoundException("User not found"));

        mvc.perform(put("/api/users/99/follow").requestAttr("userId", 1L))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_follow_shouldReturn204WithNoBody() throws Exception {
        mvc.perform(delete("/api/users/2/follow").requestAttr("userId", 1L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(followService).unfollowUser(1L, 2L);
    }

    @Test
    void delete_follow_shouldReturn404_forMissingUser() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("User not found"))
                .when(followService).unfollowUser(1L, 99L);

        mvc.perform(delete("/api/users/99/follow").requestAttr("userId", 1L))
                .andExpect(status().isNotFound());
    }

    @Test
    void followersAndFollowing_shouldDefaultToFirstPageOfTwenty() throws Exception {
        when(followService.getFollowers(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(followService.getFollowing(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/users/2/followers").requestAttr("userId", 1L)).andExpect(status().isOk());
        mvc.perform(get("/api/users/2/following").requestAttr("userId", 1L)
                .param("page", "2").param("size", "50")).andExpect(status().isOk());

        verify(followService).getFollowers(1L, 2L, 0, 20);
        verify(followService).getFollowing(1L, 2L, 2, 50);
    }

    @Test
    void followers_shouldReturn400_whenSizeIsInvalid() throws Exception {
        when(followService.getFollowers(1L, 2L, 0, 500))
                .thenThrow(new BadRequestException("page must be >= 0 and size must be between 1 and 100"));

        mvc.perform(get("/api/users/2/followers").requestAttr("userId", 1L).param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void followers_shouldNotExposeEmails() throws Exception {
        when(followService.getFollowers(1L, 2L, 0, 20)).thenReturn(List.of(
                UserResponse.builder().id(5L).name("Ben").email(null).build()));

        mvc.perform(get("/api/users/2/followers").requestAttr("userId", 1L))
                .andExpect(jsonPath("$[0].name").value("Ben"))
                .andExpect(jsonPath("$[0].email").value((Object) null));
    }

    @Test
    void getFollowStatus_shouldUseTheIsPrefixedKeys() throws Exception {
        when(followService.getFollowStatus(1L, 2L)).thenReturn(FollowStatusResponse.builder()
                .userId(2L).followedByMe(true).followingMe(false).followersCount(5).followingCount(3).build());

        mvc.perform(get("/api/users/2/follow-status").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.isFollowedByMe").value(true))
                .andExpect(jsonPath("$.isFollowingMe").value(false))
                .andExpect(jsonPath("$.followersCount").value(5))
                .andExpect(jsonPath("$.followingCount").value(3))
                // the un-prefixed Lombok-style names must not leak into the contract
                .andExpect(jsonPath("$.followedByMe").doesNotExist())
                .andExpect(jsonPath("$.followingMe").doesNotExist());
    }

    @Test
    void publicProfileJson_shouldExposeCountsAndIsFollowedByMeExactlyOnce() throws Exception {
        PublicProfileResponse profile = PublicProfileResponse.builder()
                .id(2L).name("Asha").followersCount(12).followingCount(7).followedByMe(true).build();

        JsonNode json = new ObjectMapper().valueToTree(profile);

        assertThat(json.get("followersCount").asLong()).isEqualTo(12);
        assertThat(json.get("followingCount").asLong()).isEqualTo(7);
        assertThat(json.get("isFollowedByMe").asBoolean()).isTrue();
        assertThat(json.has("followedByMe")).isFalse();
        assertThat(json.has("email")).isFalse();
    }

    // ------------------------------------------------------------ private accounts and follow requests

    @Test
    void put_follow_ofAPrivateAccount_returns200WithStatusPending() throws Exception {
        when(followService.followUser(1L, 2L)).thenReturn(FollowResponse.builder().id(10L)
                .status(com.sashank.paraspa.enums.FollowStatus.PENDING).build());

        mvc.perform(put("/api/users/2/follow").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void followLists_ofAPrivateAccountYouDoNotFollow_return403() throws Exception {
        when(followService.getFollowers(1L, 2L, 0, 20)).thenThrow(new ForbiddenException("This account is private"));

        mvc.perform(get("/api/users/2/followers").requestAttr("userId", 1L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account is private"));
    }

    @Test
    void followStatus_usesTheIsPrefixedKeys_includingTheRequestedFlag() throws Exception {
        when(followService.getFollowStatus(1L, 2L)).thenReturn(FollowStatusResponse.builder().userId(2L)
                .followedByMe(false).followRequestedByMe(true).followingMe(false).followersCount(5).followingCount(3).build());

        mvc.perform(get("/api/users/2/follow-status").requestAttr("userId", 1L))
                .andExpect(jsonPath("$.isFollowedByMe").value(false))
                .andExpect(jsonPath("$.isFollowRequestedByMe").value(true))
                .andExpect(jsonPath("$.isFollowingMe").value(false))
                .andExpect(jsonPath("$.followRequestedByMe").doesNotExist());
    }

    @Test
    void followRequests_listDefaultsToFirstPageOfTwenty() throws Exception {
        when(followService.getFollowRequests(anyLong(), anyInt(), anyInt())).thenReturn(List.of(
                com.sashank.paraspa.dto.response.FollowRequestResponse.builder().id(10L)
                        .requester(UserResponse.builder().id(5L).name("Ben").build()).build()));

        mvc.perform(get("/api/follow-requests").requestAttr("userId", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].requester.name").value("Ben"))
                .andExpect(jsonPath("$[0].requester.email").value((Object) null));

        verify(followService).getFollowRequests(2L, 0, 20);
    }

    @Test
    void acceptAndRejectRequest_return204() throws Exception {
        mvc.perform(put("/api/follow-requests/10/accept").requestAttr("userId", 2L)).andExpect(status().isNoContent());
        mvc.perform(put("/api/follow-requests/11/reject").requestAttr("userId", 2L)).andExpect(status().isNoContent());

        verify(followService).acceptFollowRequest(2L, 10L);
        verify(followService).rejectFollowRequest(2L, 11L);
    }

    @Test
    void acceptRequest_returns404_forSomebodyElsesOrMissingRequest() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Follow request not found"))
                .when(followService).acceptFollowRequest(3L, 10L);

        mvc.perform(put("/api/follow-requests/10/accept").requestAttr("userId", 3L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Follow request not found"));
    }
}
