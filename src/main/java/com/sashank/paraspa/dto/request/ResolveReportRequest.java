package com.sashank.paraspa.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveReportRequest {
    /** What was decided or done; optional. */
    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}
