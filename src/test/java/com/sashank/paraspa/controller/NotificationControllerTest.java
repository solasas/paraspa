package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.NotificationResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.enums.NotificationTargetType;
import com.sashank.paraspa.enums.NotificationType;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.service.NotificationService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(notificationController).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void list_defaultsToTwenty_andReturnsTheCursorEnvelopeWithAPublicActor() throws Exception {
        NotificationResponse n = NotificationResponse.builder().id(40L).type(NotificationType.LIKE)
                .targetType(NotificationTargetType.POST).targetId(100L).read(false)
                .actor(UserResponse.builder().id(5L).name("Ben").build()).build();
        when(notificationService.getNotifications(anyLong(), any(), anyInt())).thenReturn(new CursorPageResponse<>(List.of(n), "next"));

        mvc.perform(get("/api/notifications").requestAttr("userId", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(40))
                .andExpect(jsonPath("$.items[0].type").value("LIKE"))
                .andExpect(jsonPath("$.items[0].targetType").value("POST"))
                .andExpect(jsonPath("$.items[0].targetId").value(100))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].actor.name").value("Ben"))
                .andExpect(jsonPath("$.items[0].actor.email").value((Object) null))
                .andExpect(jsonPath("$.nextCursor").value("next"));

        verify(notificationService).getNotifications(2L, null, 20);
    }

    @Test
    void list_passesCursorAndSize_andSurfacesABadCursorAs400() throws Exception {
        when(notificationService.getNotifications(2L, "abc", 5)).thenReturn(new CursorPageResponse<>(List.of(), null));
        when(notificationService.getNotifications(2L, "bad", 20)).thenThrow(new BadRequestException("Invalid cursor"));

        mvc.perform(get("/api/notifications").requestAttr("userId", 2L).param("cursor", "abc").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextCursor").value((Object) null));
        mvc.perform(get("/api/notifications").requestAttr("userId", 2L).param("cursor", "bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid cursor"));
    }

    @Test
    void unreadCount_isABareNumber_likeTheMessagesBadge() throws Exception {
        when(notificationService.getUnreadCount(2L)).thenReturn(3L);

        mvc.perform(get("/api/notifications/unread-count").requestAttr("userId", 2L))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    @Test
    void markRead_returns204() throws Exception {
        mvc.perform(put("/api/notifications/40/read").requestAttr("userId", 2L))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(notificationService).markRead(2L, 40L);
    }

    @Test
    void markRead_returns404_forSomebodyElsesOrMissingNotification() throws Exception {
        doThrow(new ResourceNotFoundException("Notification not found")).when(notificationService).markRead(3L, 40L);

        mvc.perform(put("/api/notifications/40/read").requestAttr("userId", 3L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    void readAll_returns204_andIsNotConfusedWithAnId() throws Exception {
        mvc.perform(put("/api/notifications/read-all").requestAttr("userId", 2L)).andExpect(status().isNoContent());

        verify(notificationService).markAllRead(2L);
    }
}
