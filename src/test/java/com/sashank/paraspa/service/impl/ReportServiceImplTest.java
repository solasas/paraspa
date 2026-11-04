package com.sashank.paraspa.service.impl;

import com.sashank.paraspa.dto.request.CreateReportRequest;
import com.sashank.paraspa.dto.request.ResolveReportRequest;
import com.sashank.paraspa.dto.response.CursorPageResponse;
import com.sashank.paraspa.dto.response.ReportResponse;
import com.sashank.paraspa.dto.response.UserResponse;
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
import com.sashank.paraspa.util.CursorUtil;
import com.sashank.paraspa.util.DtoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private AccessPolicy accessPolicy;
    @Mock private DtoMapper dtoMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    private final User reporter = User.builder().id(1L).build();
    private final User author = User.builder().id(2L).name("Ben").build();
    private final Post post = Post.builder().id(100L).author(author).content("a post").build();

    @BeforeEach
    void defaultMapping() {
        org.mockito.Mockito.lenient().when(dtoMapper.toPublicUserResponse(any(User.class)))
                .thenAnswer(invocation -> UserResponse.builder().id(((User) invocation.getArgument(0)).getId()).build());
    }

    private static CreateReportRequest request(ReportTargetType type, long targetId, ReportReason reason, String details) {
        return CreateReportRequest.builder().targetType(type).targetId(targetId).reason(reason).details(details).build();
    }

    private void stubReporterAndSave() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report r = invocation.getArgument(0);
            r.setId(500L);
            return r;
        });
    }

    private Report saved() {
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        return captor.getValue();
    }

    // ================================================================ filing a report

    @Test
    void createReport_aUser_isSavedOpen_withTheReporterAndTheTrimmedDetails() {
        stubReporterAndSave();
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));

        ReportResponse response = reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.SPAM, "  sells things  "));

        Report report = saved();
        assertThat(report.getReporter()).isEqualTo(reporter);
        assertThat(report.getTargetType()).isEqualTo(ReportTargetType.USER);
        assertThat(report.getTargetId()).isEqualTo(2L);
        assertThat(report.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(report.getDetails()).isEqualTo("sells things");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(response.getReporter()).isNull();   // the reporter's own view carries no admin-only fields
        assertThat(response.getTargetPreview()).isNull();
    }

    @Test
    void createReport_blankDetailsAreStoredAsNothing() {
        stubReporterAndSave();
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));

        reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.SPAM, "   "));

        assertThat(saved().getDetails()).isNull();
    }

    @Test
    void createReport_reasonOTHER_needsAnExplanation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.OTHER, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("OTHER");
        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.OTHER, "   ")))
                .isInstanceOf(BadRequestException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_reasonOTHER_withDetails_isFine() {
        stubReporterAndSave();
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));

        reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.OTHER, "pretends to be someone"));

        assertThat(saved().getDetails()).isEqualTo("pretends to be someone");
    }

    // ================================================================ duplicates

    @Test
    void createReport_theSameTargetTwice_isRefused_409_andNothingIsSaved() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.USER, 2L)).thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.SPAM, null)))
                .isInstanceOf(AlreadyExistsException.class).hasMessage("You have already reported this");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_aDifferentReasonDoesNotMakeItANewReport_oneReportPerReporterAndTarget() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.USER, 2L)).thenReturn(true);

        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.HARASSMENT, null)))
                .isInstanceOf(AlreadyExistsException.class);
    }

    @Test
    void createReport_theSameIdButAnotherTargetTypeIsADifferentTarget() {
        // user 2 and post 2 are different things; only an exact (type, id) pair counts as a duplicate
        stubReporterAndSave();
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.USER, 2L)).thenReturn(false);

        reportService.createReport(1L, request(ReportTargetType.USER, 2L, ReportReason.SPAM, null));

        verify(reportRepository, never()).existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 2L);
        verify(reportRepository).save(any(Report.class));
    }

    // ================================================================ what can be reported

    @Test
    void createReport_aPostIsSavedAfterAVisibilityCheck() {
        stubReporterAndSave();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        reportService.createReport(1L, request(ReportTargetType.POST, 100L, ReportReason.VIOLENCE, null));

        verify(accessPolicy).requirePostVisible(1L, post);
        assertThat(saved().getTargetType()).isEqualTo(ReportTargetType.POST);
    }

    @Test
    void createReport_aCommentIsSavedAfterAVisibilityCheckOfItsPost() {
        stubReporterAndSave();
        Comment comment = Comment.builder().id(7L).post(post).author(author).content("rude").build();
        when(commentRepository.findById(7L)).thenReturn(Optional.of(comment));

        reportService.createReport(1L, request(ReportTargetType.COMMENT, 7L, ReportReason.HARASSMENT, null));

        verify(accessPolicy).requirePostVisible(1L, post);
        assertThat(saved().getTargetType()).isEqualTo(ReportTargetType.COMMENT);
    }

    @Test
    void createReport_aPostYouCouldNotSee_looksLikeAMissingPost() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        doThrow(new ResourceNotFoundException("Post not found")).when(accessPolicy).requirePostVisible(1L, post);

        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.POST, 100L, ReportReason.SPAM, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_blockAndReport_aPostOfSomeoneYouBlockedCanStillBeReported() {
        stubReporterAndSave();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));
        when(blockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);   // I blocked the author

        reportService.createReport(1L, request(ReportTargetType.POST, 100L, ReportReason.HARASSMENT, null));

        verify(accessPolicy, never()).requirePostVisible(any(), any());   // the block would otherwise hide the post I want to report
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void createReport_unknownTargets_are404() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        when(postRepository.findById(9L)).thenReturn(Optional.empty());
        when(commentRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.USER, 9L, ReportReason.SPAM, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("User not found");
        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.POST, 9L, ReportReason.SPAM, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Post not found");
        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.COMMENT, 9L, ReportReason.SPAM, null)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Comment not found");
    }

    @Test
    void createReport_yourselfAndYourOwnContent_is400() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        Post mine = Post.builder().id(101L).author(reporter).build();
        Comment myComment = Comment.builder().id(8L).post(post).author(reporter).build();
        when(postRepository.findById(101L)).thenReturn(Optional.of(mine));
        when(commentRepository.findById(8L)).thenReturn(Optional.of(myComment));

        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.USER, 1L, ReportReason.SPAM, null)))
                .isInstanceOf(BadRequestException.class).hasMessage("You cannot report yourself");
        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.POST, 101L, ReportReason.SPAM, null)))
                .isInstanceOf(BadRequestException.class).hasMessage("You cannot report your own content");
        assertThatThrownBy(() -> reportService.createReport(1L, request(ReportTargetType.COMMENT, 8L, ReportReason.SPAM, null)))
                .isInstanceOf(BadRequestException.class).hasMessage("You cannot report your own content");

        verify(reportRepository, never()).save(any());
    }

    // ================================================================ the moderators' queue

    private Report open(long id, ReportTargetType type, long targetId) {
        return Report.builder().id(id).reporter(User.builder().id(1L).build()).targetType(type).targetId(targetId)
                .reason(ReportReason.SPAM).status(ReportStatus.OPEN).build();
    }

    @Test
    void getReports_listsOldestFirst_withAPreviewAndTheNumberOfReportsOnEachTarget() {
        String longText = "x".repeat(300);
        Report onUser = open(1, ReportTargetType.USER, 2L);
        Report onPost = open(2, ReportTargetType.POST, 100L);
        Report onImagePost = open(3, ReportTargetType.POST, 101L);
        Report onComment = open(4, ReportTargetType.COMMENT, 7L);
        Report onGone = open(5, ReportTargetType.POST, 999L);
        Report extra = open(6, ReportTargetType.USER, 2L);       // the extra row of the page (size 5): only signals "more"
        when(reportRepository.findByStatusOrderByIdAsc(eq(ReportStatus.OPEN), any(Pageable.class)))
                .thenReturn(List.of(onUser, onPost, onImagePost, onComment, onGone, extra));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(author));
        when(postRepository.findAllById(anyCollection())).thenReturn(List.of(
                Post.builder().id(100L).author(author).content(longText).build(),
                Post.builder().id(101L).author(author).content(null).build()));
        when(commentRepository.findAllById(anyCollection())).thenReturn(List.of(
                Comment.builder().id(7L).post(post).author(author).content("a\n  rude   comment").build()));
        when(reportRepository.countByTarget(eq(ReportTargetType.USER), anyCollection())).thenReturn(List.<Object[]>of(new Object[]{2L, 3L}));
        when(reportRepository.countByTarget(eq(ReportTargetType.POST), anyCollection())).thenReturn(List.<Object[]>of(new Object[]{100L, 2L}));
        when(reportRepository.countByTarget(eq(ReportTargetType.COMMENT), anyCollection())).thenReturn(List.of());

        CursorPageResponse<ReportResponse> page = reportService.getReports(ReportStatus.OPEN, null, 5);

        assertThat(page.getItems()).extracting(ReportResponse::getId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(page.getNextCursor()).isNotNull();
        assertThat(CursorUtil.decode(page.getNextCursor())).isEqualTo(5L);
        assertThat(page.getItems().get(0).getTargetPreview()).isEqualTo("Ben");
        assertThat(page.getItems().get(1).getTargetPreview()).hasSize(123).endsWith("...");       // 120 characters + "..."
        assertThat(page.getItems().get(2).getTargetPreview()).isEqualTo("[image post]");
        assertThat(page.getItems().get(3).getTargetPreview()).isEqualTo("a rude comment");        // whitespace collapsed
        assertThat(page.getItems().get(4).getTargetPreview()).isEqualTo("[deleted]");             // the post is gone, the report stays
        assertThat(page.getItems().get(0).getReportsOnTarget()).isEqualTo(3L);
        assertThat(page.getItems().get(1).getReportsOnTarget()).isEqualTo(2L);
        assertThat(page.getItems().get(3).getReportsOnTarget()).isEqualTo(1L);                    // no row in the count: just this report
        assertThat(page.getItems().get(0).getReporter().getId()).isEqualTo(1L);
    }

    @Test
    void getReports_usesAFixedNumberOfQueries_onePerTargetTypeNotOnePerReport() {
        List<Report> many = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            many.add(open(i, ReportTargetType.POST, 100 + i));
        }
        when(reportRepository.findByStatusOrderByIdAsc(eq(ReportStatus.OPEN), any(Pageable.class))).thenReturn(many);
        when(postRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(reportRepository.countByTarget(eq(ReportTargetType.POST), anyCollection())).thenReturn(List.of());

        reportService.getReports(ReportStatus.OPEN, null, 20);

        verify(postRepository, times(1)).findAllById(anyCollection());
        verify(reportRepository, times(1)).countByTarget(eq(ReportTargetType.POST), anyCollection());
        verify(userRepository, never()).findAllById(anyCollection());
    }

    @Test
    void getReports_continuesAfterTheCursor_andFiltersByStatus() {
        when(reportRepository.findByStatusAndIdGreaterThanOrderByIdAsc(eq(ReportStatus.RESOLVED), eq(40L), any(Pageable.class)))
                .thenReturn(List.of());

        CursorPageResponse<ReportResponse> page = reportService.getReports(ReportStatus.RESOLVED, CursorUtil.encode(40L), 20);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getNextCursor()).isNull();
        verify(reportRepository, never()).findByStatusOrderByIdAsc(any(), any());
    }

    @Test
    void getReports_rejectsABadCursorAndBadSizes() {
        assertThatThrownBy(() -> reportService.getReports(ReportStatus.OPEN, "garbage!", 20))
                .isInstanceOf(BadRequestException.class).hasMessage("Invalid cursor");
        assertThatThrownBy(() -> reportService.getReports(ReportStatus.OPEN, null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> reportService.getReports(ReportStatus.OPEN, null, 101)).isInstanceOf(BadRequestException.class);
    }

    // ================================================================ resolving

    @Test
    void resolveReport_closesItWithWhoAndWhenAndTheNote() {
        User admin = User.builder().id(9L).build();
        Report report = open(10, ReportTargetType.USER, 2L);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(author));
        when(reportRepository.countByTarget(eq(ReportTargetType.USER), anyCollection())).thenReturn(List.of());

        ReportResponse response = reportService.resolveReport(9L, 10L, ResolveReportRequest.builder().note("  warned the user ").build());

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getResolvedBy()).isEqualTo(admin);
        assertThat(report.getResolvedAt()).isNotNull();
        assertThat(report.getResolutionNote()).isEqualTo("warned the user");
        verify(reportRepository).save(report);
        assertThat(response.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(response.getResolvedBy().getId()).isEqualTo(9L);
    }

    @Test
    void resolveReport_aMissingBodyIsFine_noNote() {
        Report report = open(10, ReportTargetType.USER, 2L);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(userRepository.findById(9L)).thenReturn(Optional.of(User.builder().id(9L).build()));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(reportRepository.countByTarget(any(), anyCollection())).thenReturn(List.of());

        reportService.resolveReport(9L, 10L, null);

        assertThat(report.getResolutionNote()).isNull();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    void resolveReport_twice_isRefused_andTheFirstNoteIsKept() {
        Report report = open(10, ReportTargetType.USER, 2L);
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolutionNote("first decision");
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.resolveReport(9L, 10L, ResolveReportRequest.builder().note("second").build()))
                .isInstanceOf(BadRequestException.class).hasMessage("This report is already resolved");

        assertThat(report.getResolutionNote()).isEqualTo("first decision");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void resolveReport_unknownReport_is404() {
        when(reportRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.resolveReport(9L, 10L, null))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Report not found");
    }
}
