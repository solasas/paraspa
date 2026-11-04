package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.CreateReportRequest;
import com.sashank.paraspa.dto.response.ReportResponse;
import com.sashank.paraspa.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportResponse> createReport(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(userId, request));
    }
}
