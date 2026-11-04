package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.ReportResponse;
import com.sashank.paraspa.dto.response.UserResponse;
import com.sashank.paraspa.enums.ReportReason;
import com.sashank.paraspa.enums.ReportStatus;
import com.sashank.paraspa.enums.ReportTargetType;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.GlobalExceptionHandler;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.service.BlockService;
import com.sashank.paraspa.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
class SafetyControllersTest {

    @Mock private BlockService blockService;
    @Mock private ReportService reportService;

    @InjectMocks private BlockController blockController;
    @InjectMocks private ReportController reportController;
    @InjectMocks private AdminReportController adminReportController;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(blockController, reportController, adminReportController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // ================================================================ blocks

    @Test
    void putBlock_returns204_andDeleteBlockReturns204() throws Exception {
        mvc.perform(put("/api/users/2/block").requestAttr("userId", 1L)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete("/api/users/2/block").requestAttr("userId", 1L)).andExpect(status().isNoContent());

        verify(blockService).block(1L, 2L);
        verify(blockService).unblock(1L, 2L);
    }

    @Test
    void putBlock_ofYourselfIs400_andOfAMissingUserIs404() throws Exception {
        doThrow(new BadRequestException("You cannot block yourself")).when(blockService).block(1L, 1L);
        doThrow(new ResourceNotFoundException("User not found")).when(blockService).block(1L, 99L);

        mvc.perform(put("/api/users/1/block").requestAttr("userId", 1L))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("You cannot block yourself"));
        mvc.perform(put("/api/users/99/block").requestAttr("userId", 1L)).andExpect(status().isNotFound());
    }

    @Test
    void getBlocks_listsMyBlockedUsers_withoutEmails_andDefaultsToTwenty() throws Exception {
        when(blockService.getBlockedUsers(eq(1L), anyInt(), anyInt())).thenReturn(List.of(UserResponse.builder().id(2L).name("Ben").build()));

        mvc.perform(get("/api/blocks").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ben"))
                .andExpect(jsonPath("$[0].email").value((Object) null));

        verify(blockService).getBlockedUsers(1L, 0, 20);
    }

    @Test
    void getBlocks_badPagingIs400() throws Exception {
        when(blockService.getBlockedUsers(1L, 0, 500)).thenThrow(new BadRequestException("size must be between 1 and 100"));

        mvc.perform(get("/api/blocks").requestAttr("userId", 1L).param("size", "500")).andExpect(status().isBadRequest());
    }

    // ================================================================ reports

    @Test
    void postReport_returns201WithTheReport() throws Exception {
        when(reportService.createReport(eq(1L), any())).thenReturn(ReportResponse.builder().id(500L)
                .targetType(ReportTargetType.POST).targetId(100L).reason(ReportReason.SPAM).status(ReportStatus.OPEN).build());

        mvc.perform(post("/api/reports").requestAttr("userId", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":100,\"reason\":\"SPAM\",\"details\":\"ads\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reporter").value((Object) null));
    }

    @Test
    void postReport_validatesTheBody() throws Exception {
        mvc.perform(post("/api/reports").requestAttr("userId", 1L).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("targetType"),
                        org.hamcrest.Matchers.containsString("targetId"),
                        org.hamcrest.Matchers.containsString("reason"))));
        mvc.perform(post("/api/reports").requestAttr("userId", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":1,\"reason\":\"SPAM\",\"details\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1000")));
    }

    @Test
    void postReport_theSameTargetTwiceIs409() throws Exception {
        when(reportService.createReport(eq(1L), any())).thenThrow(new AlreadyExistsException("You have already reported this"));

        mvc.perform(post("/api/reports").requestAttr("userId", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"USER\",\"targetId\":2,\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already reported this"));
    }

    // ================================================================ admin reports

    @Test
    void adminListing_defaultsToOpenReports_firstPage_cursorEnvelope() throws Exception {
        when(reportService.getReports(any(), any(), anyInt())).thenReturn(new CursorPageResponse<>(
                List.of(ReportResponse.builder().id(1L).targetPreview("a post").reportsOnTarget(3L).build()), "next"));

        mvc.perform(get("/api/admin/reports").requestAttr("userId", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetPreview").value("a post"))
                .andExpect(jsonPath("$.items[0].reportsOnTarget").value(3))
                .andExpect(jsonPath("$.nextCursor").value("next"));

        verify(reportService).getReports(ReportStatus.OPEN, null, 20);
    }

    @Test
    void adminListing_acceptsAStatusCursorAndSize_andRejectsAnUnknownStatus() throws Exception {
        when(reportService.getReports(ReportStatus.RESOLVED, "abc", 5)).thenReturn(new CursorPageResponse<>(List.of(), null));

        mvc.perform(get("/api/admin/reports").requestAttr("userId", 9L).param("status", "RESOLVED").param("cursor", "abc").param("size", "5"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports").requestAttr("userId", 9L).param("status", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));
    }

    @Test
    void adminResolve_passesTheAdminAndTheNote_andAllowsAMissingBody() throws Exception {
        when(reportService.resolveReport(eq(9L), eq(10L), any())).thenReturn(ReportResponse.builder().id(10L).status(ReportStatus.RESOLVED).build());

        mvc.perform(put("/api/admin/reports/10/resolve").requestAttr("userId", 9L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"removed the post\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(put("/api/admin/reports/10/resolve").requestAttr("userId", 9L)).andExpect(status().isOk());
    }

    @Test
    void adminResolve_twiceIs400_unknownIs404() throws Exception {
        when(reportService.resolveReport(eq(9L), eq(10L), any())).thenThrow(new BadRequestException("This report is already resolved"));
        when(reportService.resolveReport(eq(9L), eq(99L), any())).thenThrow(new ResourceNotFoundException("Report not found"));

        mvc.perform(put("/api/admin/reports/10/resolve").requestAttr("userId", 9L)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/reports/99/resolve").requestAttr("userId", 9L)).andExpect(status().isNotFound());
    }

    @Test
    void theAdminControllerIsProtectedByMethodSecurity_soAPlainUserCannotCallItEvenIfTheUrlRuleIsDropped() {
        PreAuthorize annotation = AdminReportController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).as("@PreAuthorize on AdminReportController").isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }
}
