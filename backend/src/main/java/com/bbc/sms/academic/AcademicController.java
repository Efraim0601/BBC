package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/academic")
public class AcademicController {

    private final AcademicService service;
    private final SessionAcademicService sessionService;
    private final BulletinSnapshotService snapshotService;
    private final GradeEntryService gradeEntryService;
    private final ReportCardInputService reportCardInputService;
    private final ReportCardBatchService reportCardBatchService;
    private final ReportCardBatchJobService reportCardBatchJobService;
    private final ReportCardPdfService reportCardPdfService;
    private final OfficialDocumentService officialDocuments;
    private final AssessmentDefaultsService assessmentDefaults;
    private final IdempotencyService idempotency;

    public AcademicController(AcademicService service, SessionAcademicService sessionService, BulletinSnapshotService snapshotService, GradeEntryService gradeEntryService, ReportCardInputService reportCardInputService, ReportCardBatchService reportCardBatchService, ReportCardBatchJobService reportCardBatchJobService, ReportCardPdfService reportCardPdfService, OfficialDocumentService officialDocuments, AssessmentDefaultsService assessmentDefaults, IdempotencyService idempotency) { this.service = service; this.sessionService = sessionService; this.snapshotService = snapshotService; this.gradeEntryService = gradeEntryService; this.reportCardInputService = reportCardInputService; this.reportCardBatchService = reportCardBatchService; this.reportCardBatchJobService = reportCardBatchJobService; this.reportCardPdfService = reportCardPdfService; this.officialDocuments = officialDocuments; this.assessmentDefaults = assessmentDefaults; this.idempotency = idempotency; }

    @GetMapping("/students/{studentId}/grades")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public List<GradeView> listForStudent(@PathVariable UUID studentId) {
        return service.listForStudent(studentId);
    }

    @PostMapping("/grades")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public GradeView upsert(@Valid @RequestBody GradeUpsert in) {
        throw com.bbc.sms.platform.common.ApiException.coded(HttpStatus.GONE, "CANONICAL_GRADE_PACKET_REQUIRED",
                "La saisie directe est désactivée. Utilisez la feuille de notes par classe et matière.");
    }

    @GetMapping("/reporting-periods/{periodId}/assessments")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public List<AssessmentView> assessments(@PathVariable UUID periodId,
                                             @RequestParam(required = false) UUID classId,
                                             @RequestParam(required = false) String subjectCode) {
        return sessionService.assessments(periodId, classId, subjectCode);
    }

    @PostMapping("/assessments")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public AssessmentView createAssessment(@Valid @RequestBody AssessmentUpsert in) { return sessionService.createAssessment(in); }

    @PutMapping("/assessments/{id}")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public AssessmentView updateAssessment(@PathVariable UUID id, @Valid @RequestBody AssessmentUpsert in) {
        return sessionService.updateAssessment(id, in);
    }

    @DeleteMapping("/assessments/{id}")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssessment(@PathVariable UUID id, @RequestParam(required = false) Long version) {
        sessionService.deleteAssessment(id, version);
    }

    @PostMapping("/assessment-defaults/preview")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public AssessmentDefaultsPreview previewAssessmentDefaults(
            @Valid @RequestBody AssessmentDefaultsPreviewRequest request) {
        return assessmentDefaults.preview(request);
    }

    @PostMapping("/assessment-defaults/apply")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public AssessmentDefaultsApplyResponse applyAssessmentDefaults(
            @Valid @RequestBody AssessmentDefaultsPreviewRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return idempotency.execute("academic.assessment-defaults.apply", idempotencyKey, request,
                AssessmentDefaultsApplyResponse.class, () -> assessmentDefaults.apply(request, idempotencyKey));
    }

    @GetMapping("/students/{studentId}/session-grades")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public List<AcademicGradeView> sessionGrades(@PathVariable UUID studentId, @RequestParam UUID reportingPeriodId) { return sessionService.grades(studentId, reportingPeriodId); }

    @PostMapping("/session-grades")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public AcademicGradeView upsertSessionGrade(@Valid @RequestBody AcademicGradeUpsert in) {
        throw com.bbc.sms.platform.common.ApiException.coded(HttpStatus.GONE, "CANONICAL_GRADE_PACKET_REQUIRED",
                "La saisie directe est désactivée. Utilisez la feuille de notes par classe et matière.");
    }

    @PostMapping("/subject-comments")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public SubjectResultCommentView upsertSubjectComment(@Valid @RequestBody SubjectResultCommentUpsert in) {
        throw com.bbc.sms.platform.common.ApiException.coded(HttpStatus.GONE, "CANONICAL_GRADE_PACKET_REQUIRED",
                "La saisie directe des remarques est désactivée. Utilisez la feuille de notes par classe et matière.");
    }

    @PostMapping("/students/{studentId}/bulletin-snapshots")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinSnapshotView calculateSnapshot(@PathVariable UUID studentId, @RequestParam UUID reportingPeriodId) { return snapshotService.calculate(studentId, reportingPeriodId); }

    @GetMapping("/students/{studentId}/bulletin-snapshots/preview")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public BulletinSnapshotView previewSnapshot(@PathVariable UUID studentId, @RequestParam UUID reportingPeriodId) {
        return snapshotService.preview(studentId, reportingPeriodId);
    }

    @GetMapping("/students/{studentId}/bulletin-snapshots/latest")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public BulletinSnapshotView latestSnapshot(@PathVariable UUID studentId, @RequestParam UUID reportingPeriodId) { return snapshotService.latest(studentId, reportingPeriodId); }

    @PostMapping("/bulletin-snapshots/{id}/correction")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinSnapshotView startCorrection(@PathVariable UUID id, @Valid @RequestBody BulletinCorrectionRequest request) {
        return snapshotService.startCorrection(id, request);
    }

    @PostMapping("/bulletin-snapshots/{id}/refresh")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinSnapshotView refreshSnapshot(@PathVariable UUID id, @Valid @RequestBody BulletinRefreshRequest request) {
        return snapshotService.refresh(id, request);
    }

    @GetMapping("/bulletin-snapshots/{id}/pdf")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public ResponseEntity<byte[]> reportCardPdf(@PathVariable UUID id, @RequestParam(defaultValue = "fr") String locale) {
        BulletinSnapshotView snapshot = snapshotService.byId(id);
        if (!"VALIDATED".equals(snapshot.state()) && !"PUBLISHED".equals(snapshot.state())) {
            throw com.bbc.sms.platform.common.ApiException.badRequest("Le bulletin doit être validé avant la génération du document officiel");
        }
        byte[] pdf = reportCardPdfService.render(id, !"en".equalsIgnoreCase(locale));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=bulletin-" + id + ".pdf")
                .cacheControl(CacheControl.noStore()).body(pdf);
    }

    @PostMapping("/bulletin-snapshots/{id}/document")
    @PreAuthorize("@perm.canAction('DOCUMENT_GENERATE')")
    public GeneratedDocumentView generateOfficialReportCard(@PathVariable UUID id,
                                                             @RequestParam(defaultValue = "fr") String locale,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BulletinSnapshotView snapshot = snapshotService.byId(id);
        if (!"VALIDATED".equals(snapshot.state()) && !"PUBLISHED".equals(snapshot.state())) {
            throw com.bbc.sms.platform.common.ApiException.badRequest("Le bulletin doit être validé avant la génération du document officiel");
        }
        byte[] pdf = reportCardPdfService.render(id, !"en".equalsIgnoreCase(locale));
        return officialDocuments.registerPdf("REPORT_CARD", "BulletinVersion", id.toString(), String.valueOf(snapshot.version()), locale,
                ("en".equalsIgnoreCase(locale) ? "School report card" : "Bulletin scolaire") + " - " + snapshot.studentName(), "PARENT", pdf, idempotencyKey);
    }

    @PostMapping("/bulletin-snapshots/{id}/validate")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinSnapshotView validateSnapshot(@PathVariable UUID id) { return snapshotService.validate(id); }

    @PostMapping("/bulletin-snapshots/{id}/publish")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinSnapshotView publishSnapshot(@PathVariable UUID id, @Valid @RequestBody BulletinLifecycleRequest request) {
        return snapshotService.publish(id, request);
    }

    @GetMapping("/classes/{classId}/pv-snapshot")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public SessionPvView classPv(@PathVariable UUID classId, @RequestParam UUID reportingPeriodId) {
        return snapshotService.classPv(classId, reportingPeriodId);
    }

    @PostMapping("/classes/{classId}/bulletin-batch")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public ResponseEntity<byte[]> bulletinBatch(@PathVariable UUID classId, @RequestParam UUID reportingPeriodId,
                                                 @RequestParam(defaultValue = "fr") String locale) {
        byte[] zip = reportCardBatchService.render(classId, reportingPeriodId, locale);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bulletins-" + classId + "-" + reportingPeriodId + ".zip")
                .cacheControl(CacheControl.noStore()).body(zip);
    }

    @PostMapping("/bulletin-batch-jobs")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinBatchJobView createBulletinBatchJob(@Valid @RequestBody BulletinBatchJobCreateRequest request) {
        return reportCardBatchJobService.create(request);
    }

    @GetMapping("/bulletin-batch-jobs/{id}")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public BulletinBatchJobView bulletinBatchJob(@PathVariable UUID id) { return reportCardBatchJobService.view(id); }

    @GetMapping("/bulletin-batch-jobs")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public List<BulletinBatchJobView> bulletinBatchJobs(@RequestParam UUID classId, @RequestParam UUID reportingPeriodId) {
        return reportCardBatchJobService.list(classId, reportingPeriodId);
    }

    @GetMapping("/bulletin-batch-jobs/{id}/items")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public List<BulletinBatchItemView> bulletinBatchJobItems(@PathVariable UUID id) { return reportCardBatchJobService.items(id); }

    @PostMapping("/bulletin-batch-jobs/{id}/retry")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinBatchJobView retryBulletinBatchJob(@PathVariable UUID id, @RequestParam(required = false) UUID itemId) {
        return reportCardBatchJobService.retry(id, itemId);
    }

    @PostMapping("/bulletin-batch-jobs/{id}/cancel")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public BulletinBatchJobView cancelBulletinBatchJob(@PathVariable UUID id,
                                                        @Valid @RequestBody BulletinBatchCancelRequest request) {
        return reportCardBatchJobService.cancel(id, request);
    }

    @GetMapping("/bulletin-batch-jobs/{id}/download")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public ResponseEntity<byte[]> downloadBulletinBatchJob(@PathVariable UUID id) {
        byte[] archive = reportCardBatchJobService.archive(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bulletin-batch-" + id + ".zip")
                .cacheControl(CacheControl.noStore()).body(archive);
    }

    @GetMapping("/grade-entry")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public GradeEntryView gradeEntry(@RequestParam UUID reportingPeriodId, @RequestParam UUID classId,
                                     @RequestParam(required = false) String subjectCode) {
        return gradeEntryService.view(reportingPeriodId, classId, subjectCode);
    }

    @PostMapping("/grade-entry/save")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public GradeEntryView saveGradeEntry(@Valid @RequestBody GradeEntrySaveRequest request) {
        return gradeEntryService.save(request);
    }

    @PostMapping("/grade-entry/workflow")
    @PreAuthorize("@perm.canAction('GRADE_SUBMIT') and @perm.staffOnly()")
    public GradeEntryView gradeEntryWorkflow(@Valid @RequestBody GradeEntryReviewRequest request) {
        return gradeEntryService.submit(request);
    }

    @GetMapping("/report-card-inputs")
    @PreAuthorize("@perm.can('academic','read') and @perm.staffOnly()")
    public ReportCardInputsView reportCardInputs(@RequestParam UUID reportingPeriodId, @RequestParam UUID classId) {
        return reportCardInputService.list(reportingPeriodId, classId);
    }

    @PutMapping("/report-card-inputs")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public ReportCardInputsView saveReportCardInputs(@Valid @RequestBody ReportCardInputUpsert request) {
        return reportCardInputService.save(request);
    }

    @PostMapping("/report-card-inputs/{studentId}/submit")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public ReportCardInputsView submitReportCardInputs(@PathVariable UUID studentId,
                                                        @RequestParam UUID reportingPeriodId,
                                                        @RequestParam UUID classId) {
        return reportCardInputService.submit(reportingPeriodId, classId, studentId);
    }

    @PostMapping("/report-card-inputs/{studentId}/review")
    @PreAuthorize("@perm.can('academic','write') and @perm.staffOnly()")
    public ReportCardInputsView reviewReportCardInputs(@PathVariable UUID studentId,
                                                        @Valid @RequestBody ReportCardInputReview request) {
        return reportCardInputService.review(request.reportingPeriodId(), request.classId(), studentId, request);
    }
}
