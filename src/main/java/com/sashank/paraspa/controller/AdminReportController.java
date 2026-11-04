package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.ResolveReportRequest;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.ReportResponse;
import com.sashank.paraspa.enums.ReportStatus;
import com.sashank.paraspa.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Moderation. Admins only: the role comes from the database on every request (see JwtAuthenticationFilter). */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    @Autowired
    private ReportService reportService;

    /** The reports with the given status (default OPEN), oldest first. */
    @GetMapping
    public ResponseEntity<CursorPageResponse<ReportResponse>> getReports(
            @RequestParam(defaultValue = "OPEN") ReportStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reportService.getReports(status, cursor, size));
    }

    @PutMapping("/{reportId}/resolve")
    public ResponseEntity<ReportResponse> resolveReport(
            @RequestAttribute("userId") Long adminId,
            @PathVariable Long reportId,
            @Valid @RequestBody(required = false) ResolveReportRequest request) {
        return ResponseEntity.ok(reportService.resolveReport(adminId, reportId, request));
    }
}
