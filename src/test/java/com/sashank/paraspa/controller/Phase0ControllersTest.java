package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.ProfileResponse;
import com.sashank.paraspa.dto.response.PublicProfileResponse;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.service.AvailabilityService;
import com.sashank.paraspa.service.ExchangeService;
import com.sashank.paraspa.service.MessageService;
import com.sashank.paraspa.service.PublicProfileService;
import com.sashank.paraspa.service.UserSearchService;
import com.sashank.paraspa.service.UserService;
import com.sashank.paraspa.service.UserSkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MVC tests: routing, multipart binding, default paging and error mapping. */
@ExtendWith(MockitoExtension.class)
class Phase0ControllersTest {

    @Mock private UserService userService;
    @Mock private UserSkillService userSkillService;
    @Mock private AvailabilityService availabilityService;
    @Mock private UserSearchService userSearchService;
    @Mock private PublicProfileService publicProfileService;
    @Mock private ExchangeService exchangeService;
    @Mock private MessageService messageService;

    @InjectMocks private ProfileController profileController;
    @InjectMocks private UserController userController;
    @InjectMocks private ExchangeController exchangeController;
    @InjectMocks private MessageController messageController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(profileController, userController, exchangeController, messageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void putAvatar_shouldBindMultipartFilePartAndReturnProfile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        when(userService.updateAvatar(eq(5L), any())).thenReturn(
                ProfileResponse.builder().id(5L).avatarUrl("/files/avatars/5-x.png").build());

        mvc.perform(multipart("/api/profile/me/avatar")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .requestAttr("userId", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/files/avatars/5-x.png"));
    }

    @Test
    void putAvatar_shouldReturn400_whenFilePartMissing() throws Exception {
        mvc.perform(multipart("/api/profile/me/avatar")
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .requestAttr("userId", 5L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putAvatar_shouldReturn400WithErrorBody_whenServiceRejectsFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1});
        when(userService.updateAvatar(anyLong(), any()))
                .thenThrow(new BadRequestException("Avatar must be a JPG, PNG or WebP image"));

        mvc.perform(multipart("/api/profile/me/avatar")
                        .file(file)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .requestAttr("userId", 5L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Avatar must be a JPG, PNG or WebP image"));
    }

    @Test
    void getPublicProfile_shouldReturnProfileWithoutEmailKey() throws Exception {
        when(publicProfileService.getPublicProfile(1L, 2L)).thenReturn(
                PublicProfileResponse.builder().id(2L).name("Asha").followersCount(4).build());

        mvc.perform(get("/api/users/2").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.followersCount").value(4))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void getPublicProfile_shouldNotShadowSearchRoute() throws Exception {
        when(userSearchService.searchUsers(anyLong(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mvc.perform(get("/api/users/search").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void search_shouldDefaultToFirstPageOfTwenty() throws Exception {
        when(userSearchService.searchUsers(anyLong(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mvc.perform(get("/api/users/search").requestAttr("userId", 1L)).andExpect(status().isOk());

        verify(userSearchService).searchUsers(1L, null, null, null, null, null, 0, 20);
    }

    @Test
    void exchangeLists_shouldDefaultToFirstPageOfTwenty_andPassExplicitValues() throws Exception {
        when(exchangeService.getMyExchanges(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(exchangeService.getSentExchanges(anyLong(), anyInt(), anyInt())).thenReturn(List.of());
        when(exchangeService.getReceivedExchanges(anyLong(), anyInt(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/exchanges").requestAttr("userId", 1L)).andExpect(status().isOk());
        mvc.perform(get("/api/exchanges/sent").requestAttr("userId", 1L).param("page", "2").param("size", "50"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/exchanges/received").requestAttr("userId", 1L)).andExpect(status().isOk());

        verify(exchangeService).getMyExchanges(1L, 0, 20);
        verify(exchangeService).getSentExchanges(1L, 2, 50);
        verify(exchangeService).getReceivedExchanges(1L, 0, 20);
    }

    @Test
    void conversation_shouldDefaultToFirstPageOfTwenty() throws Exception {
        when(messageService.getConversation(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/messages/conversations/2").requestAttr("userId", 1L)).andExpect(status().isOk());

        verify(messageService).getConversation(1L, 2L, 0, 20);
    }

    @Test
    void pagination_shouldReturn400_whenSizeIsNotANumber() throws Exception {
        mvc.perform(get("/api/exchanges").requestAttr("userId", 1L).param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'size'"));
    }

    @Test
    void pagination_shouldReturn400_whenServiceRejectsSize() throws Exception {
        when(exchangeService.getMyExchanges(1L, 0, 500))
                .thenThrow(new BadRequestException("page must be >= 0 and size must be between 1 and 100"));

        mvc.perform(get("/api/exchanges").requestAttr("userId", 1L).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must be >= 0 and size must be between 1 and 100"));
    }
}
