package com.sashank.paraspa.service;

import com.sashank.paraspa.dto.request.CreateReportRequest;
import com.sashank.paraspa.dto.request.ResolveReportRequest;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.ReportResponse;
import com.sashank.paraspa.enums.ReportStatus;

public interface ReportService {

    /**
     * Files a report. The target must exist (404); you cannot report yourself or your own content (400); reason OTHER
     * needs details (400); a second report of the same target by the same person is refused (409).
     * You can report something you blocked the author of, but not a post you could not see anyway.
     */
    ReportResponse createReport(Long reporterId, CreateReportRequest request);

    /** Admin: the reports with the given status, oldest first, cursor-paginated, with a preview of what was reported. */
    CursorPageResponse<ReportResponse> getReports(ReportStatus status, String cursor, int size);

    /** Admin: closes a report with an optional note. Resolving twice is a 400. */
    ReportResponse resolveReport(Long adminId, Long reportId, ResolveReportRequest request);
}
