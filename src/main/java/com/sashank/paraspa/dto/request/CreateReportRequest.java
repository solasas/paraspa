package com.sashank.paraspa.dto.request;

import com.sashank.paraspa.enums.ReportReason;
import com.sashank.paraspa.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReportRequest {
    @NotNull(message = "Target type is required")
    private ReportTargetType targetType;

    @NotNull(message = "Target id is required")
    private Long targetId;

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    /** Optional, except that reason OTHER needs it. */
    @Size(max = 1000, message = "Details must be at most 1000 characters")
    private String details;
}
