package com.sashank.paraspa.dto.response;

import com.sashank.paraspa.enums.ReportReason;
import com.sashank.paraspa.enums.ReportStatus;
import com.sashank.paraspa.enums.ReportTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * A report. The reporter gets the first group of fields back when they file it; the moderation API (admins) also
 * fills in the second group.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {
    private Long id;
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReason reason;
    private String details;
    private ReportStatus status;
    private LocalDateTime createdAt;

    // ---- admin view only
    private UserResponse reporter;
    /** A short description of what was reported: the user's name, or the start of the post/comment; "[deleted]" if gone. */
    private String targetPreview;
    /** How many users reported this same target: the more, the more likely it is a real problem. */
    private Long reportsOnTarget;
    private LocalDateTime resolvedAt;
    private UserResponse resolvedBy;
    private String resolutionNote;
}
