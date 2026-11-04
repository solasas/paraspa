package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateReportRequest;
import com.sashank.paraspa.dto.request.ResolveReportRequest;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.ReportResponse;
import com.sashank.paraspa.entity.Comment;
import com.sashank.paraspa.entity.Post;
import com.sashank.paraspa.entity.Report;
import com.sashank.paraspa.entity.User;
import com.sashank.paraspa.enums.ReportReason;
import com.sashank.paraspa.enums.ReportStatus;
import com.sashank.paraspa.enums.ReportTargetType;
import com.sashank.paraspa.exception.AlreadyExistsException;
import com.sashank.paraspa.exception.BadRequestException;
import com.sashank.paraspa.exception.ResourceNotFoundException;
import com.sashank.paraspa.repository.BlockRepository;
import com.sashank.paraspa.repository.CommentRepository;
import com.sashank.paraspa.repository.PostRepository;
import com.sashank.paraspa.repository.ReportRepository;
import com.sashank.paraspa.repository.UserRepository;
import com.sashank.paraspa.service.AccessPolicy;
import com.sashank.paraspa.service.ReportService;
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import com.sashank.paraspa.util.PaginationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReportServiceImpl implements ReportService {

    private static final int PREVIEW_LENGTH = 120;
    private static final String DELETED = "[deleted]";

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private AccessPolicy accessPolicy;

    @Autowired
    private DtoMapper dtoMapper;

    @Override
    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String details = request.getDetails() == null || request.getDetails().isBlank() ? null : request.getDetails().trim();
        if (request.getReason() == ReportReason.OTHER && details == null) {
            throw new BadRequestException("Please describe the problem when the reason is OTHER");
        }

        requireReportableTarget(reporterId, request.getTargetType(), request.getTargetId());

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.getTargetType(), request.getTargetId())) {
            throw new AlreadyExistsException("You have already reported this");
        }

        // the unique constraint (reporter, target type, target id) also stops two simultaneous identical reports (409)
        Report report = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .details(details)
                .build());

        return ReportResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ReportResponse> getReports(ReportStatus status, String cursor, int size) {
        PaginationUtil.requireValidSize(size);
        Long afterId = CursorUtil.decode(cursor);

        Pageable pageable = PageRequest.of(0, size + 1);   // one extra row tells us whether a next page exists
        List<Report> fetched = afterId == null
                ? reportRepository.findByStatusOrderByIdAsc(status, pageable)
                : reportRepository.findByStatusAndIdGreaterThanOrderByIdAsc(status, afterId, pageable);

        CursorPageResponse<Report> page = CursorPageResponse.of(fetched, size, Report::getId);
        return new CursorPageResponse<>(toAdminResponses(page.getItems()), page.getNextCursor());
    }

    @Override
    @Transactional
    public ReportResponse resolveReport(Long adminId, Long reportId, ResolveReportRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getStatus() == ReportStatus.RESOLVED) {
            throw new BadRequestException("This report is already resolved");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String note = request == null || request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedAt(LocalDateTime.now());
        report.setResolvedBy(admin);
        report.setResolutionNote(note);
        reportRepository.save(report);

        return toAdminResponses(List.of(report)).get(0);
    }

    // ------------------------------------------------------------------ helpers

    /** The target exists, is not the reporter's own, and is something the reporter could have seen. */
    private void requireReportableTarget(Long reporterId, ReportTargetType type, Long targetId) {
        switch (type) {
            case USER -> {
                User target = userRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                if (target.getId().equals(reporterId)) {
                    throw new BadRequestException("You cannot report yourself");
                }
                if (!blockedByReporter(reporterId, target.getId())) {
                    accessPolicy.requireUserVisible(reporterId, target.getId());
                }
            }
            case POST -> {
                Post post = postRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
                requireOthersContent(reporterId, post.getAuthor());
                if (!blockedByReporter(reporterId, post.getAuthor().getId())) {
                    accessPolicy.requirePostVisible(reporterId, post);
                }
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
                requireOthersContent(reporterId, comment.getAuthor());
                if (!blockedByReporter(reporterId, comment.getAuthor().getId())
                        && !blockedByReporter(reporterId, comment.getPost().getAuthor().getId())) {
                    accessPolicy.requirePostVisible(reporterId, comment.getPost());
                }
            }
        }
    }

    private void requireOthersContent(Long reporterId, User author) {
        if (author.getId().equals(reporterId)) {
            throw new BadRequestException("You cannot report your own content");
        }
    }

    /** Someone who blocked the author can still report what they saw before blocking (block and report). */
    private boolean blockedByReporter(Long reporterId, Long authorId) {
        return blockRepository.existsByBlockerIdAndBlockedId(reporterId, authorId);
    }

    /** Builds the moderators' view of a page of reports with a fixed number of queries (previews and counts in batches). */
    private List<ReportResponse> toAdminResponses(List<Report> reports) {
        Map<ReportTargetType, Set<Long>> idsByType = new HashMap<>();
        for (Report report : reports) {
            idsByType.computeIfAbsent(report.getTargetType(), t -> new HashSet<>()).add(report.getTargetId());
        }

        Map<ReportTargetType, Map<Long, String>> previews = new HashMap<>();
        Map<ReportTargetType, Map<Long, Long>> counts = new HashMap<>();
        for (Map.Entry<ReportTargetType, Set<Long>> entry : idsByType.entrySet()) {
            ReportTargetType type = entry.getKey();
            Set<Long> ids = entry.getValue();
            previews.put(type, loadPreviews(type, ids));
            Map<Long, Long> perTarget = new HashMap<>();
            for (Object[] row : reportRepository.countByTarget(type, ids)) {
                perTarget.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
            counts.put(type, perTarget);
        }

        return reports.stream().map(report -> ReportResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .reporter(dtoMapper.toPublicUserResponse(report.getReporter()))
                .targetPreview(previews.get(report.getTargetType()).getOrDefault(report.getTargetId(), DELETED))
                .reportsOnTarget(counts.get(report.getTargetType()).getOrDefault(report.getTargetId(), 1L))
                .resolvedAt(report.getResolvedAt())
                .resolvedBy(dtoMapper.toPublicUserResponse(report.getResolvedBy()))
                .resolutionNote(report.getResolutionNote())
                .build()).toList();
    }

    private Map<Long, String> loadPreviews(ReportTargetType type, Set<Long> ids) {
        Map<Long, String> previews = new HashMap<>();
        switch (type) {
            case USER -> userRepository.findAllById(ids).forEach(u -> previews.put(u.getId(), u.getName()));
            case POST -> postRepository.findAllById(ids).forEach(p -> previews.put(p.getId(),
                    p.getContent() == null || p.getContent().isBlank() ? "[image post]" : excerpt(p.getContent())));
            case COMMENT -> commentRepository.findAllById(ids).forEach(c -> previews.put(c.getId(), excerpt(c.getContent())));
        }
        return previews;
    }

    private static String excerpt(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "...";
    }
}
