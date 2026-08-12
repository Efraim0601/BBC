package com.bbc.sms.academic;

import com.bbc.sms.academic.calculation.AcademicCalculationEngine;
import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.attendance.AttendanceEvidenceService;
import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceSummaryView;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import com.bbc.sms.documents.OfficialDocumentDtos.GeneratedDocumentView;
import com.bbc.sms.documents.OfficialDocumentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BulletinSnapshotService implements AuthoritativeBulletinSnapshotReader {
    private static final String FORMULA_VERSION = "AcademicCalculationEngine/v2-live-dependencies";
    private final AcademicReportingPeriodRepository periods;
    private final AcademicAssessmentRepository assessments;
    private final AcademicGradeRepository grades;
    private final AcademicGradePacketRepository packets;
    private final SubjectResultCommentRepository comments;
    private final BulletinVersionRepository versions;
    private final StudentEnrollmentRepository enrollments;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final SubjectClassCoefRepository subjectClassCoefs;
    private final SchoolClassRepository classes;
    private final AcademicWindowPolicyService windows;
    private final TeacherScopeService teacherScope;
    private final TeachingAssignmentResolver assignments;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final AttendanceEvidenceService attendanceEvidence;
    private final ReportCardPdfService reportCardPdf;
    private final OfficialDocumentService officialDocuments;
    private final BulletinPublicationOutboxService publicationOutbox;

    public BulletinSnapshotService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                   AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                   AcademicGradePacketRepository packets, BulletinVersionRepository versions, StudentEnrollmentRepository enrollments,
                                   StudentRepository students, SubjectRepository subjects,
                                   SubjectClassCoefRepository subjectClassCoefs, SchoolClassRepository classes,
                                   AcademicWindowPolicyService windows, TeacherScopeService teacherScope, TeachingAssignmentResolver assignments, ObjectMapper mapper,
                                   JdbcTemplate jdbc, AuditService audit, AttendanceEvidenceService attendanceEvidence,
                                   @Lazy ReportCardPdfService reportCardPdf, OfficialDocumentService officialDocuments,
                                   BulletinPublicationOutboxService publicationOutbox) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments; this.packets = packets;
        this.versions = versions; this.enrollments = enrollments; this.students = students; this.subjects = subjects;
        this.subjectClassCoefs = subjectClassCoefs; this.classes = classes;
        this.windows = windows; this.teacherScope = teacherScope; this.assignments = assignments; this.mapper = mapper; this.jdbc = jdbc; this.audit = audit;
        this.attendanceEvidence = attendanceEvidence; this.reportCardPdf = reportCardPdf;
        this.officialDocuments = officialDocuments; this.publicationOutbox = publicationOutbox;
    }

    private void setPublicationIdentity(BulletinVersion version, AcademicReportingPeriod period,
                                        SnapshotTrace trace) {
        String code = period.getCode() == null ? "" : period.getCode().toUpperCase(Locale.ROOT);
        String product = "ANNUAL_RESULT".equalsIgnoreCase(period.getPeriodType()) ? "ANNUAL"
                : "T3_RESULT".equals(code) ? "T3"
                : "TERM_RESULT".equalsIgnoreCase(period.getPeriodType()) ? "TERM" : "SEQUENCE";
        version.setPublicationProduct(product);
        String locale = trace == null || trace.documentDesign() == null
                || trace.documentDesign().locale() == null ? "fr" : trace.documentDesign().locale();
        version.setPublicationLocale(locale.toLowerCase(Locale.ROOT));
    }

    private UUID recordLifecycleTransition(BulletinVersion version, String fromState, String toState,
                                           String eventType, String reason, UUID sourceVersionId,
                                           UUID generatedDocumentId, List<String> affectedRows) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("state", fromState);
        before.put("version", version.getVersion());
        before.put("snapshotHash", version.getSnapshotHash());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("state", toState);
        after.put("version", version.getVersion());
        after.put("snapshotHash", version.getSnapshotHash());
        after.put("templateVersion", version.getTemplateVersion());
        after.put("generatedDocumentId", generatedDocumentId);
        UUID auditId = audit.recordWithId(eventType, "BulletinVersion", version.getId().toString(),
                before, after, reason);
        jdbc.update("""
                INSERT INTO bulletin_lifecycle_transition
                    (school_id,bulletin_version_id,source_version_id,from_state,to_state,
                     event_type,actor_user_id,reason,source_versions,optimistic_version,
                     calculation_snapshot_hash,template_version,generated_document_id,
                     audit_event_id,affected_rows)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?,?, ?,? ,?::jsonb)
                """, TenantContext.get(), version.getId(), sourceVersionId, fromState, toState,
                eventType, currentUserId(), clip(reason), sourceVersionsJson(version), version.getVersion(),
                version.getSnapshotHash(), version.getTemplateVersion(), generatedDocumentId, auditId,
                jsonArray(affectedRows));
        return auditId;
    }

    private String sourceVersionsJson(BulletinVersion version) {
        try {
            var root = mapper.readTree(version.getSnapshotJson());
            var sourceVersions = root.path("snapshot").path("sourceVersions");
            return sourceVersions.isMissingNode() ? "[]" : sourceVersions.toString();
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private String jsonArray(List<String> values) {
        try { return mapper.writeValueAsString(values == null ? List.of() : values); }
        catch (JsonProcessingException ex) { return "[]"; }
    }

    private static String clip(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        return clean.length() <= 1000 ? clean : clean.substring(0, 1000);
    }

    private static List<String> allowedTransitions(String state) {
        return switch (state == null ? "" : state) {
            case "DRAFT" -> List.of("TEACHER_SUBMITTED", "VALIDATED");
            case "TEACHER_SUBMITTED" -> List.of("REVIEW", "RETURNED");
            case "REVIEW" -> List.of("VALIDATED", "RETURNED");
            case "RETURNED" -> List.of("DRAFT", "TEACHER_SUBMITTED", "VALIDATED");
            case "VALIDATED" -> List.of("PUBLISHED");
            case "PUBLISHED" -> List.of("SUPERSEDED");
            default -> List.of();
        };
    }

    private StudentEnrollment enrollment(UUID studentId, AcademicReportingPeriod period) {
        return enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
    }

    private BulletinVersion latestOfficial(UUID studentId, UUID periodId) {
        BulletinVersion published = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                TenantContext.get(), studentId, periodId, "PUBLISHED").orElse(null);
        if (published != null) return published;
        return versions.findBySchoolIdAndStudentIdAndReportingPeriodIdAndState(
                        TenantContext.get(), studentId, periodId, "VALIDATED").stream()
                .max(Comparator.comparing(BulletinVersion::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private BulletinVersion latestActive(UUID studentId, UUID periodId) {
        return versions.findBySchoolIdAndStudentIdAndReportingPeriodIdAndStateInOrderByCreatedAtDesc(
                        TenantContext.get(), studentId, periodId, List.of("DRAFT", "RETURNED"))
                .stream().findFirst().orElse(null);
    }

    private CurrentSnapshot currentSnapshot(UUID studentId, AcademicReportingPeriod period,
                                            Student student, StudentEnrollment enrollment) {
        CalculationContext context = new CalculationContext();
        Calculation raw = calculateCurrent(studentId, period, context);
        Calculation calculation = withClassStatistics(studentId, period, raw, context);
        AttendanceSummaryView attendance = attendance(period, studentId);
        ConductSummaryView conduct = conduct(period, studentId);
        SnapshotTrace trace = snapshotTrace(period, student, enrollment, calculation);
        SnapshotDocument document = writeSnapshot(period, student, enrollment, calculation, attendance, conduct, trace);
        return new CurrentSnapshot(student, enrollment, calculation, attendance, conduct, trace,
                document.json(), document.canonicalHash());
    }

    private List<String> officialBlockers(Calculation calculation, AttendanceSummaryView attendance,
                                          ConductSummaryView conduct, String periodType) {
        List<String> blockers = new ArrayList<>(calculation == null ? List.of() : calculation.blockers());
        boolean reportCardEvidence = Set.of("TERM_RESULT", "ANNUAL_RESULT").contains(
                periodType == null ? "" : periodType.toUpperCase(Locale.ROOT));
        if (reportCardEvidence && attendance != null) {
            attendance.blockers().forEach(issue -> addDistinct(blockers, issue.code()));
            if (attendance.annualDraftRequired()) addDistinct(blockers, "ATTENDANCE_ANNUAL_DRAFT_REQUIRED");
        }
        if (reportCardEvidence && (conduct == null || !Set.of("APPROVED", "LOCKED", "LOCKED_BY_PUBLICATION").contains(
                conduct.status() == null ? "" : conduct.status().toUpperCase(Locale.ROOT))))
            addDistinct(blockers, "CONDUCT_NOT_APPROVED");
        return blockers;
    }

    private BulletinSnapshotView persistedView(BulletinVersion version, AcademicReportingPeriod period,
                                               Student student, CurrentSnapshot current,
                                               String relation, boolean refreshRequired) {
        return view(version, period, student, current.calculation(), current.attendance(), current.conduct(),
                current.trace(), relation, refreshRequired);
    }

    private BulletinSnapshotView currentView(CurrentSnapshot current, AcademicReportingPeriod period,
                                             Student student, BulletinVersion persisted,
                                             String relation, boolean refreshRequired) {
        BulletinVersion transientVersion = new BulletinVersion();
        transientVersion.setId(persisted == null ? null : persisted.getId());
        transientVersion.setState(persisted == null ? "PREVIEW" : persisted.getState());
        transientVersion.setVersion(persisted == null ? 0 : persisted.getVersion());
        transientVersion.setSnapshotHash(current.hash());
        transientVersion.setCalculationPolicy(period.getCalculationPolicy());
        transientVersion.setSupersedesId(persisted == null ? null : persisted.getSupersedesId());
        transientVersion.setCorrectsBulletinVersionId(persisted == null ? null : persisted.getCorrectsBulletinVersionId());
        transientVersion.setCorrectionReason(persisted == null ? null : persisted.getCorrectionReason());
        transientVersion.setCorrectionRequestedBy(persisted == null ? null : persisted.getCorrectionRequestedBy());
        transientVersion.setCorrectionRequestedAt(persisted == null ? null : persisted.getCorrectionRequestedAt());
        return view(transientVersion, period, student, current.calculation(), current.attendance(), current.conduct(),
                current.trace(), relation, refreshRequired);
    }

    @Transactional
    public BulletinSnapshotView calculate(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(periodId);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollment(studentId, period);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée. Vérifiez son inscription dans Élèves > Inscription.");
        BulletinVersion official = latestOfficial(studentId, periodId);
        BulletinVersion active = latestActive(studentId, periodId);
        if (official != null && active == null) return viewFromSnapshot(official, period, student);
        CurrentSnapshot current = currentSnapshot(studentId, period, student, enrollment);
        List<String> officialBlockers = officialBlockers(current.calculation(), current.attendance(), current.conduct(), period.getPeriodType());
        if (active != null) {
            if (Objects.equals(active.getSnapshotHash(), current.hash()) && officialBlockers.isEmpty())
                return persistedView(active, period, student, current, "CURRENT", false);
            throw ApiException.blockers("BULLETIN_DRAFT_STALE",
                    "Le brouillon de " + period.getCode() + " ne correspond plus aux sources actuelles. Actualisez-le avant validation.",
                    List.of("BULLETIN_DRAFT_STALE"));
        }
        if (!officialBlockers.isEmpty()) {
            throw ApiException.blockers("BULLETIN_NOT_READY",
                    "Le bulletin ne peut pas être créé tant que les sources académiques et administratives ne sont pas prêtes.",
                    officialBlockers);
        }
        BulletinVersion version = new BulletinVersion();
        version.setSchoolId(TenantContext.get()); version.setAcademicSessionId(period.getAcademicSessionId()); version.setReportingPeriodId(periodId);
        version.setStudentId(studentId); version.setEnrollmentId(enrollment.getId()); version.setState("DRAFT");
        version.setSnapshotJson(current.json());
        version.setSnapshotHash(current.hash()); version.setAverage(current.calculation().average() == null ? BigDecimal.ZERO : current.calculation().average());
        version.setRank(current.calculation().rank()); version.setClassSize(current.calculation().classSize());
        version.setCalculationPolicy(period.getCalculationPolicy()); version.setCreatedBy(currentUserId());
        version.setSnapshotContractVersion(1); version.setGenerationActorId(currentUserId());
        version.setGenerationTime(Instant.now()); version.setCanonicalSnapshotHash(current.hash());
        version.setSourceVersionFingerprint(current.trace().sourceHash());
        version.setTemplateVersion(templateReference(current.trace()));
        freezeDesign(version, current.trace(), enrollment);
        setPublicationIdentity(version, period, current.trace());
        BulletinVersion saved = versions.save(version);
        persistSourceIndex(saved.getId(), current);
        audit.record("BULLETIN_DRAFT_CREATED", "BulletinVersion", saved.getId().toString(), null,
                Map.of("id", saved.getId(), "periodCode", period.getCode(), "studentId", studentId,
                        "snapshotHash", saved.getSnapshotHash(), "average", saved.getAverage()), null);
        return view(saved, period, student, current.calculation(), current.attendance(), current.conduct(), current.trace(),
                "CURRENT", false);
    }

    @Transactional
    public BulletinSnapshotView refresh(UUID id, BulletinRefreshRequest request) {
        BulletinVersion previous = versions.findByIdAndSchoolIdForUpdate(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!Set.of("DRAFT", "RETURNED").contains(previous.getState())) {
            throw ApiException.conflict("Seuls les brouillons et les bulletins retournés peuvent être actualisés.");
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("Le motif d'actualisation est obligatoire.");
        }
        if (request.version() == null || request.version() != previous.getVersion()) {
            long supplied = request.version() == null ? -1 : request.version();
            throw ApiException.staleVersion("Le brouillon a été modifié entre-temps. Rechargez-le avant de l'actualiser.",
                    previous.getVersion(), supplied);
        }
        teacherScope.assertStudent(previous.getStudentId());
        windows.assertOpen(previous.getReportingPeriodId(), AcademicWindowPolicyService.Action.VALIDATION);
        AcademicReportingPeriod period = period(previous.getReportingPeriodId());
        Student student = students.findByIdAndSchoolId(previous.getStudentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollment(previous.getStudentId(), period);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour l'actualisation.");
        CurrentSnapshot current = currentSnapshot(previous.getStudentId(), period, student, enrollment);
        List<String> blockers = officialBlockers(current.calculation(), current.attendance(), current.conduct(), period.getPeriodType());
        if (!blockers.isEmpty()) {
            throw ApiException.blockers("BULLETIN_NOT_READY",
                    "Le brouillon ne peut pas être actualisé tant que ses sources ne sont pas prêtes.", blockers);
        }
        if (Objects.equals(previous.getSnapshotHash(), current.hash())) {
            return persistedView(previous, period, student, current, "CURRENT", false);
        }

        String oldHash = previous.getSnapshotHash();
        BigDecimal oldAverage = previous.getAverage();
        previous.setState("SUPERSEDED");
        versions.saveAndFlush(previous);

        BulletinVersion replacement = new BulletinVersion();
        replacement.setSchoolId(TenantContext.get());
        replacement.setAcademicSessionId(period.getAcademicSessionId());
        replacement.setReportingPeriodId(period.getId());
        replacement.setStudentId(previous.getStudentId());
        replacement.setEnrollmentId(enrollment.getId());
        replacement.setState("DRAFT");
        replacement.setSnapshotJson(current.json());
        replacement.setSnapshotHash(current.hash());
        replacement.setAverage(current.calculation().average() == null ? BigDecimal.ZERO : current.calculation().average());
        replacement.setRank(current.calculation().rank());
        replacement.setClassSize(current.calculation().classSize());
        replacement.setCalculationPolicy(period.getCalculationPolicy());
        replacement.setCreatedBy(currentUserId());
        replacement.setSnapshotContractVersion(1); replacement.setGenerationActorId(currentUserId());
        replacement.setGenerationTime(Instant.now()); replacement.setCanonicalSnapshotHash(current.hash());
        replacement.setSourceVersionFingerprint(current.trace().sourceHash());
        replacement.setSupersedesId(previous.getId());
        replacement.setGeneralAppreciation(previous.getGeneralAppreciation());
        replacement.setTemplateVersion(templateReference(current.trace()));
        freezeDesign(replacement, current.trace(), enrollment);
        setPublicationIdentity(replacement, period, current.trace());
        BulletinVersion saved = versions.saveAndFlush(replacement);
        persistSourceIndex(saved.getId(), current);
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("id", previous.getId()); before.put("state", "DRAFT"); before.put("snapshotHash", oldHash); before.put("average", oldAverage);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("id", saved.getId()); after.put("state", "DRAFT"); after.put("snapshotHash", saved.getSnapshotHash()); after.put("average", saved.getAverage());
        after.put("supersedesId", previous.getId()); after.put("reason", request.reason().trim());
        audit.record("BULLETIN_DRAFT_REFRESHED", "BulletinVersion", saved.getId().toString(), before, after, request.reason().trim());
        return persistedView(saved, period, student, current, "CURRENT", false);
    }

    /**
     * Start a named correction without mutating the validated/published
     * snapshot. The replacement is recalculated from the current grade,
     * attendance and council inputs and can follow the normal validate ->
     * publish workflow.
     */
    @Transactional
    public BulletinSnapshotView startCorrection(UUID id, BulletinCorrectionRequest request) {
        BulletinVersion previous = versions.findByIdAndSchoolIdForUpdate(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!"PUBLISHED".equals(previous.getState()))
            throw ApiException.conflict("Une correction ne peut commencer que depuis un bulletin validé ou publié");
        if (request == null || request.reason() == null || request.reason().isBlank())
            throw ApiException.badRequest("Le motif de correction est obligatoire");
        if (request.version() == null || request.version() != previous.getVersion())
            throw ApiException.conflict("Le bulletin a été modifié entre-temps. Rechargez-le avant de corriger.");
        windows.assertOpen(previous.getReportingPeriodId(), AcademicWindowPolicyService.Action.CORRECTION);
        AcademicReportingPeriod period = period(previous.getReportingPeriodId());
        Student student = students.findByIdAndSchoolId(previous.getStudentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), previous.getStudentId(), period.getAcademicSessionId(), "ACTIVE")
                .orElseThrow(() -> ApiException.conflict("Aucune inscription active pour la correction"));
        CurrentSnapshot current = currentSnapshot(previous.getStudentId(), period, student, enrollment);
        Calculation calculation = current.calculation();
        AttendanceSummaryView attendance = current.attendance();
        ConductSummaryView conduct = current.conduct();
        SnapshotTrace trace = current.trace();
        String json = current.json();
        BulletinVersion replacement = new BulletinVersion();
        replacement.setSchoolId(TenantContext.get());
        replacement.setAcademicSessionId(period.getAcademicSessionId());
        replacement.setReportingPeriodId(period.getId());
        replacement.setStudentId(previous.getStudentId());
        replacement.setEnrollmentId(enrollment.getId());
        replacement.setState("DRAFT");
        replacement.setSnapshotJson(json);
        replacement.setSnapshotHash(current.hash());
        replacement.setAverage(calculation.average());
        replacement.setRank(calculation.rank());
        replacement.setClassSize(calculation.classSize());
        replacement.setCalculationPolicy(period.getCalculationPolicy());
        replacement.setCreatedBy(currentUserId());
        replacement.setSnapshotContractVersion(1); replacement.setGenerationActorId(currentUserId());
        replacement.setGenerationTime(Instant.now()); replacement.setCanonicalSnapshotHash(current.hash());
        replacement.setSourceVersionFingerprint(current.trace().sourceHash());
        replacement.setCorrectsBulletinVersionId(previous.getId());
        replacement.setSupersedesId(previous.getId());
        replacement.setCorrectionReason(request.reason().trim());
        replacement.setCorrectionRequestedBy(currentUserId());
        replacement.setCorrectionRequestedAt(Instant.now());
        replacement.setTemplateVersion(templateReference(trace));
        freezeDesign(replacement, trace, enrollment);
        setPublicationIdentity(replacement, period, trace);
        BulletinVersion saved = versions.save(replacement);
        persistSourceIndex(saved.getId(), current);
        recordLifecycleTransition(saved, null, "DRAFT", "BULLETIN_CORRECTION_DRAFT_CREATED",
                request.reason().trim(), previous.getId(), null, List.of(previous.getId().toString()));
        return view(saved, period, student, calculation, attendance, conduct, trace);
    }

    @Transactional(readOnly = true)
    public BulletinSnapshotView latest(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(periodId);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        BulletinVersion version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(TenantContext.get(), studentId, periodId)
                .orElseThrow(() -> ApiException.notFound("Aucun calcul de bulletin"));
        return viewFromSnapshot(version, period, student);
    }

    /** Pure calculation used by read-only class PV and preview screens. */
    @Transactional(readOnly = true)
    public BulletinSnapshotView preview(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(periodId);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Ã‰lÃ¨ve"));
        StudentEnrollment enrollment = enrollment(studentId, period);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée.");
        BulletinVersion active = latestActive(studentId, periodId);
        // An official result is a historical document. Do not calculate from
        // mutable curriculum, assignment, profile, attendance, or conduct data
        // merely to decide how to render it. The frozen payload is authoritative
        // once no editable draft is active.
        if (active == null) {
            BulletinVersion official = latestOfficial(studentId, periodId);
            if (official != null) return viewFromSnapshot(official, period, student);
        }
        // A preview never creates a version. If an explicit draft/correction already
        // exists, show that durable version so the user can continue its workflow;
        // otherwise calculate an in-memory preview from the current authoritative
        // inputs.
        CurrentSnapshot current = currentSnapshot(studentId, period, student, enrollment);
        if (active != null) {
            if (Objects.equals(active.getSnapshotHash(), current.hash()))
                return persistedView(active, period, student, current, "CURRENT", false);
            return currentView(current, period, student, active, "STALE", true);
        }
        return currentView(current, period, student, null, "NONE", false);
    }

    @Transactional(readOnly = true)
    public BulletinSnapshotView byId(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        teacherScope.assertStudent(version.getStudentId());
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        return viewFromSnapshot(version, period, student);
    }

    /**
     * Read only the serialized BAY-35 contract for official rendering.  This
     * method intentionally does not load the mutable student, class,
     * curriculum, teacher, attendance, or conduct tables.
     */
    @Transactional(readOnly = true)
    public AuthoritativeSnapshotView authoritativeById(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        teacherScope.assertStudent(version.getStudentId());
        SnapshotPayload payload = readPayload(version);
        if (payload.snapshot() == null) {
            throw ApiException.conflict("Ce bulletin ne contient pas le contrat de snapshot BAY-35");
        }
        return payload.snapshot();
    }

    @Transactional
    public BulletinSnapshotView validate(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolIdForUpdate(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!"DRAFT".equals(version.getState()) && !"RETURNED".equals(version.getState())) throw ApiException.conflict("Cette version n'est plus un brouillon validable");
        windows.assertOpen(version.getReportingPeriodId(), AcademicWindowPolicyService.Action.VALIDATION);
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get()).orElseThrow();
        StudentEnrollment enrollment = enrollment(version.getStudentId(), period);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour la validation.");
        CurrentSnapshot current = currentSnapshot(version.getStudentId(), period, student, enrollment);
        if (!Objects.equals(version.getSnapshotHash(), current.hash())) {
            throw ApiException.blockers("BULLETIN_DRAFT_STALE",
                    "Le brouillon ne correspond plus aux sources actuelles. Actualisez-le avant validation.",
                    List.of("BULLETIN_DRAFT_STALE"));
        }
        List<String> blockers = officialBlockers(current.calculation(), current.attendance(), current.conduct(), period.getPeriodType());
        if (!blockers.isEmpty()) throw ApiException.blockers("BULLETIN_NOT_READY",
                "Bulletin incomplet ou preuves administratives non approuvées : " + String.join("; ", blockers), blockers);
        String previousState = version.getState();
        version.setState("VALIDATED"); version.setValidatedAt(Instant.now()); version.setValidatedBy(currentUserId());
        versions.saveAndFlush(version);
        recordLifecycleTransition(version, previousState, "VALIDATED",
                "BULLETIN_VALIDATED", null, null, null, List.of());
        return view(version, period, student, current.calculation(), current.attendance(), current.conduct(), current.trace(), "CURRENT", false);
    }

    @Transactional
    public BulletinSnapshotView publish(UUID id, BulletinLifecycleRequest request) {
        BulletinVersion version = versions.findByIdAndSchoolIdForUpdate(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!"VALIDATED".equals(version.getState())) {
            throw ApiException.conflict("Le bulletin doit être validé avant publication. État actuel : " + version.getState());
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("Le motif de publication est obligatoire");
        }
        if (request.version() != null && request.version() != version.getVersion()) {
            throw ApiException.conflict("Le bulletin a été modifié entre-temps. Rechargez-le avant de publier.");
        }
        windows.assertOpen(version.getReportingPeriodId(), AcademicWindowPolicyService.Action.PUBLICATION);
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get()).orElseThrow();
        StudentEnrollment enrollment = enrollment(version.getStudentId(), period);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour la publication.");
        CurrentSnapshot current = currentSnapshot(version.getStudentId(), period, student, enrollment);
        if (!Objects.equals(version.getSnapshotHash(), current.hash())) {
            throw ApiException.blockers("BULLETIN_DRAFT_STALE",
                    "Le bulletin validé ne correspond plus aux sources actuelles.", List.of("BULLETIN_DRAFT_STALE"));
        }
        List<String> blockers = officialBlockers(current.calculation(), current.attendance(), current.conduct(), period.getPeriodType());
        if (!blockers.isEmpty()) throw ApiException.blockers("BULLETIN_NOT_READY",
                "Le bulletin ne peut pas être publié : " + String.join("; ", blockers), blockers);
        setPublicationIdentity(version, period, current.trace());
        version.setState("PUBLISHED");
        version.setPublishedAt(Instant.now());
        version.setPublishedBy(currentUserId());
        version.setPublicationReason(request.reason().trim());
        // Flush the state before document registration. The document service
        // deliberately refuses a parent document unless the same transaction
        // has staged PUBLISHED; any renderer/storage/ledger failure rolls back.
        versions.saveAndFlush(version);
        byte[] pdf = reportCardPdf.render(version.getId(), "en".equalsIgnoreCase(version.getPublicationLocale()));
        var frozen = authoritativeById(version.getId());
        var evidence = reportCardPdf.evidence(version.getId());
        GeneratedDocumentView document = officialDocuments.registerPublishedPdf(
                "REPORT_CARD", "BulletinVersion", version.getId().toString(), String.valueOf(version.getVersion()),
                version.getPublicationLocale(),
                ("en".equalsIgnoreCase(version.getPublicationLocale()) ? "School report card" : "Bulletin scolaire")
                        + " - " + (frozen.student() == null ? "" : frozen.student().name()),
                "PARENT", pdf,
                "bulletin-publication:" + version.getId() + ":" + version.getVersion() + ":" + version.getPublicationLocale(),
                evidence);
        version.setGeneratedDocumentId(document.id());
        versions.saveAndFlush(version);
        attendanceEvidence.freezeOfficialSnapshot(period, version.getStudentId(), version.getId(), current.attendance());
        attendanceEvidence.lockForPublication(period.getId(), version.getStudentId(), version.getId());

        UUID visibilityId = jdbc.queryForObject("""
                INSERT INTO bulletin_parent_visibility
                    (school_id,bulletin_version_id,student_id,reporting_period_id,publication_product,
                     generated_document_id,status,authorized_at,version)
                VALUES (?,?,?,?,?,?, 'ACTIVE', now(), 0)
                ON CONFLICT (school_id,bulletin_version_id) DO UPDATE SET
                    generated_document_id=EXCLUDED.generated_document_id,status='ACTIVE',authorized_at=now(),
                    revoked_at=NULL,revoked_by=NULL,revoked_reason=NULL,
                    version=bulletin_parent_visibility.version+1
                RETURNING id
                """, UUID.class, TenantContext.get(), version.getId(), version.getStudentId(),
                version.getReportingPeriodId(), version.getPublicationProduct(), document.id());

        if (version.getCorrectsBulletinVersionId() != null) {
            versions.findByIdAndSchoolIdForUpdate(version.getCorrectsBulletinVersionId(), TenantContext.get()).ifPresent(previous -> {
                String oldState = previous.getState();
                previous.setState("SUPERSEDED");
                previous.setSupersededAt(Instant.now());
                previous.setSupersededBy(version.getId());
                versions.saveAndFlush(previous);
                jdbc.update("""
                        UPDATE bulletin_parent_visibility
                           SET status='SUPERSEDED', revoked_at=now(), revoked_by=?,
                               revoked_reason=?, version=version+1
                         WHERE school_id=? AND bulletin_version_id=?
                        """, currentUserId(), request.reason().trim(), TenantContext.get(), previous.getId());
                if (previous.getGeneratedDocumentId() != null) {
                    jdbc.update("UPDATE generated_document SET status='SUPERSEDED' WHERE school_id=? AND id=? AND status='ISSUED'",
                            TenantContext.get(), previous.getGeneratedDocumentId());
                }
                recordLifecycleTransition(previous, oldState, "SUPERSEDED", "BULLETIN_SUPERSEDED",
                        request.reason().trim(), version.getId(), previous.getGeneratedDocumentId(),
                        List.of(previous.getId().toString(), version.getId().toString()));
            });
        }
        recordLifecycleTransition(version, "VALIDATED", "PUBLISHED", "BULLETIN_PUBLISHED",
                request.reason().trim(), version.getCorrectsBulletinVersionId(), document.id(), List.of());
        publicationOutbox.enqueue(version.getId(), visibilityId, document.id(), version.getStudentId(),
                version.getReportingPeriodId(), version.getPublicationProduct(), version.getSnapshotHash());
        return view(version, period, student, current.calculation(), current.attendance(), current.conduct(), current.trace(), "CURRENT", false);
    }

    /** Explicit non-destructive workflow transitions used by the validation workspace. */
    @Transactional
    public BulletinSnapshotView transition(UUID id, String action, BulletinTransitionRequest request) {
        BulletinVersion version = versions.findByIdAndSchoolIdForUpdate(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (request == null || request.reason() == null || request.reason().isBlank())
            throw ApiException.badRequest("A transition reason is required.");
        long supplied = request.version() == null ? -1 : request.version();
        if (request.version() == null || request.version() != version.getVersion())
            throw ApiException.staleVersion("The bulletin changed while the workflow action was being applied; reload it first.",
                    version.getVersion(), supplied);
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String from = version.getState();
        String to = switch (normalized) {
            case "SUBMIT", "TEACHER_SUBMITTED" -> "TEACHER_SUBMITTED";
            case "REVIEW" -> "REVIEW";
            case "RETURN", "RETURNED" -> "RETURNED";
            default -> "";
        };
        if (to.isBlank() || !allowedTransitions(from).contains(to)) {
            throw ApiException.conflictWithDetails("BULLETIN_ILLEGAL_TRANSITION",
                    "The requested bulletin lifecycle transition is not allowed from the current state.",
                    Map.of("state", from, "requestedAction", normalized, "allowedTransitions", allowedTransitions(from),
                            "affectedRows", List.of(version.getId().toString()),
                            "correctiveAction", "Choose one of the allowed workflow actions and reload the current version."));
        }
        AcademicWindowPolicyService.Action windowAction = switch (to) {
            case "TEACHER_SUBMITTED" -> AcademicWindowPolicyService.Action.TEACHER_SUBMISSION;
            case "REVIEW" -> AcademicWindowPolicyService.Action.REVIEW;
            default -> AcademicWindowPolicyService.Action.REVIEW;
        };
        windows.assertOpen(version.getReportingPeriodId(), windowAction);
        version.setState(to);
        versions.saveAndFlush(version);
        recordLifecycleTransition(version, from, to, "BULLETIN_" + to, request.reason().trim(), null, null, List.of());
        return byId(id);
    }

    @Transactional(readOnly = true)
    public BulletinLifecycleStateView lifecycle(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        AcademicWindowPolicyService.WindowView window = windows.effective(version.getReportingPeriodId(),
                "PUBLISHED".equals(version.getState()) ? AcademicWindowPolicyService.Action.PUBLICATION
                        : AcademicWindowPolicyService.Action.VALIDATION);
        List<BulletinTransitionView> history = jdbc.query("""
                SELECT id,bulletin_version_id,source_version_id,from_state,to_state,event_type,actor_user_id,
                       occurred_at,reason,source_versions::text,optimistic_version,calculation_snapshot_hash,
                       template_version,generated_document_id,audit_event_id,affected_rows::text
                  FROM bulletin_lifecycle_transition
                 WHERE school_id=? AND bulletin_version_id=?
                 ORDER BY occurred_at ASC,id ASC
                """, (rs, n) -> {
            List<String> affected = List.of();
            try {
                affected = mapper.readValue(rs.getString(16), mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (Exception ignored) { }
            return new BulletinTransitionView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getObject(7, UUID.class), rs.getTimestamp(8).toInstant(), rs.getString(9), rs.getString(10),
                    rs.getLong(11), rs.getString(12), rs.getString(13), rs.getObject(14, UUID.class),
                    rs.getObject(15, UUID.class), affected);
        }, TenantContext.get(), id);
        return new BulletinLifecycleStateView(version.getId(), version.getState(), version.getPublicationProduct(),
                version.getPublicationLocale(), version.getGeneratedDocumentId(), version.getSupersedesId(),
                version.getCorrectsBulletinVersionId(), version.getVersion(), allowedTransitions(version.getState()),
                window.state(), window.governingTermCode(), window.governedPeriodCodes(), history);
    }

    @Transactional(readOnly = true)
    public BulletinSnapshotView published(UUID studentId, UUID periodId) {
        AcademicReportingPeriod period = period(periodId);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        BulletinVersion version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                        TenantContext.get(), studentId, periodId, "PUBLISHED")
                .orElseThrow(() -> ApiException.notFound("Aucun bulletin publié pour cette période"));
        return viewFromSnapshot(version, period, student);
    }

    @Transactional(readOnly = true)
    public BulletinSnapshotView publishedLatest(UUID studentId) {
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        BulletinVersion version = versions.findFirstBySchoolIdAndStudentIdAndStateOrderByPublishedAtDesc(
                        TenantContext.get(), studentId, "PUBLISHED")
                .orElseThrow(() -> ApiException.notFound("Aucun bulletin publié pour cet élève"));
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        return viewFromSnapshot(version, period, student);
    }

    /** Build the class PV from session-aware reporting-period calculations. */
    @Transactional
    public SessionPvView classPv(UUID classId, UUID periodId) {
        teacherScope.assertClass(classId);
        AcademicReportingPeriod period = period(periodId);
        SchoolClass schoolClass = classes.findByIdAndSchoolId(classId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Classe"));
        List<StudentEnrollment> roster = enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                TenantContext.get(), period.getAcademicSessionId(), classId, "ACTIVE");
        Map<UUID, BulletinSnapshotView> frozen = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : roster) {
            // PV is a product view of the same snapshot DTO.  A student with
            // no durable version still gets the read-only preview contract;
            // the PV never runs a second calculation path of its own.
            frozen.put(enrollment.getStudentId(), preview(enrollment.getStudentId(), periodId));
        }
        List<BigDecimal> cohortAverages = frozen.values().stream()
                .filter(x -> x.blockers().isEmpty() && x.average() != null)
                .map(BulletinSnapshotView::average).toList();
        List<SessionPvRow> rows = new ArrayList<>();
        for (StudentEnrollment enrollment : roster) {
            UUID studentId = enrollment.getStudentId();
            BulletinSnapshotView snapshot = frozen.get(studentId);
            BigDecimal average = snapshot.average();
            List<String> blockers = snapshot.blockers();
            Integer rank = blockers.isEmpty() && average != null
                    ? 1 + (int) cohortAverages.stream().filter(value -> value.compareTo(average) > 0).count() : null;
            rows.add(new SessionPvRow(snapshot.id(), studentId, snapshot.studentName(),
                    average, rank, snapshot.state(), blockers.isEmpty(), blockers));
        }
        rows.sort(Comparator
                .comparing(SessionPvRow::complete).reversed()
                .thenComparing(SessionPvRow::average, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SessionPvRow::studentName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        List<BigDecimal> completeAverages = rows.stream().filter(SessionPvRow::complete).map(SessionPvRow::average).toList();
        BigDecimal classAverage = completeAverages.isEmpty() ? null : completeAverages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(completeAverages.size()), AcademicCalculationEngine.CALCULATION_SCALE, RoundingMode.HALF_UP);
        return new SessionPvView(classId, schoolClass.getName(), period.getId(), period.getCode(), period.getLabel(), rows,
                classAverage, rows.size(), completeAverages.size());
    }

    private static void addDistinct(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    /** Request-scoped current-source calculation. Persisted bulletin rows are never used as inputs. */
    private Calculation calculateCurrent(UUID studentId, AcademicReportingPeriod period, CalculationContext context) {
        CalcKey key = new CalcKey(studentId, period.getId());
        Calculation cached = context.calculations.get(key);
        if (cached != null) return cached;
        if (!context.visiting.add(key)) return cycleCalculation(studentId, period);
        try {
            Calculation calculated = "SEQUENCE".equals(period.getPeriodType())
                    ? calculateSequence(studentId, period, context)
                    : calculateComputed(studentId, period, context);
            context.calculations.put(key, calculated);
            return calculated;
        } finally {
            context.visiting.remove(key);
        }
    }

    private Calculation calculateSequence(UUID studentId, AcademicReportingPeriod period, CalculationContext context) {
        Calculation base = calculateSequence(studentId, period);
        StudentEnrollment enrollment = enrollment(studentId, period);
        UUID classId = enrollment.getSchoolClassId();
        PacketReadiness packet = packetReadiness(period, classId,
                base.lines().stream().map(BulletinLineView::subjectCode).toList(), context);
        List<String> blockers = new ArrayList<>(base.blockers());
        List<BulletinIssueView> issues = new ArrayList<>(base.issues());
        for (BulletinIssueView issue : packet.issues()) {
            blockers.add(packetBlocker(period.getCode(), issue.subjectCode(), issue.code()));
            issues.add(issue);
        }
        for (String blocker : base.blockers()) issues.add(issueForBlocker(blocker, period.getCode(), base.lines()));
        String sourceHash = sourceHash(new CurrentSourceFingerprint(period.getId(), period.getVersion(), period.getPeriodType(),
                base.lines(), blockers, List.of(), packet.traces()));
        return rebuild(base, blockers, issues, packet.readinessRows(), List.of(), packet.traces(), sourceHash,
                readiness(blockers));
    }

    private Calculation calculateComputed(UUID studentId, AcademicReportingPeriod period, CalculationContext context) {
        List<DependencyRow> dependencies = dependencies(period, context);
        if (dependencies.isEmpty()) {
            return finishWith(List.of(), List.of("DEPENDENCY_MISSING"), List.of(issue("DEPENDENCY_MISSING", "ERROR",
                    period.getCode(), null, "Aucune periode enfant n'est configuree pour " + period.getCode() + ".",
                    "No child period is configured for " + period.getCode() + ".", "academic-sessions")),
                    studentId, period, List.of(), List.of(), List.of(), null, "BLOCKED");
        }

        StudentEnrollment enrollment = enrollment(studentId, period);
        Map<String, Calculation> children = new LinkedHashMap<>();
        List<DependencySourceTrace> sourceTraces = new ArrayList<>();
        List<PacketTrace> packetTraces = new ArrayList<>();
        List<DependencyReadinessView> readinessRows = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<BulletinIssueView> issues = new ArrayList<>();
        for (DependencyRow dependency : dependencies) {
            AcademicReportingPeriod childPeriod = periods.findByIdAndSchoolId(dependency.childPeriodId(), TenantContext.get()).orElse(null);
            if (childPeriod == null) {
                if (!dependency.optional()) blockers.add(dependency.childCode() + ":DEPENDENCY_MISSING");
                issues.add(issue("DEPENDENCY_MISSING", dependency.optional() ? "WARNING" : "ERROR",
                        period.getCode(), null, dependency.childCode() + " : periode enfant introuvable.",
                        dependency.childCode() + ": child period cannot be found.", "academic-sessions"));
                continue;
            }
            BulletinVersion officialChild = latestOfficial(studentId, childPeriod.getId());
            boolean frozenChild = officialChild != null;
            Calculation child = frozenChild ? calculationFromSnapshot(officialChild) : calculateCurrent(studentId, childPeriod, context);
            children.put(dependency.childCode().toUpperCase(Locale.ROOT), child);
            if (!dependency.optional()) {
                for (String childBlocker : child.blockers()) addDistinct(blockers, dependency.childCode() + ":" + childBlocker);
                issues.addAll(child.issues());
            }
            packetTraces.addAll(child.packetTraces());
            readinessRows.add(readinessFor(dependency, child, childPeriod));
            sourceTraces.add(new DependencySourceTrace(childPeriod.getId(), childPeriod.getCode(), childPeriod.getVersion(),
                    dependency.weight(), dependency.optional(), frozenChild ? "OFFICIAL_SNAPSHOT" : sourceKind(childPeriod),
                    child.sourceHash(), child.packetTraces()));
        }

        LinkedHashSet<String> subjectCodes = curriculumSubjectCodes(studentId, period, enrollment.getSchoolClassId());
        if (subjectCodes.isEmpty()) children.values().stream().flatMap(child -> child.lines().stream())
                .map(BulletinLineView::subjectCode).forEach(subjectCodes::add);
        List<BulletinLineView> lines = new ArrayList<>();
        AcademicCalculationEngine.Product product = product(period);
        Map<String, Subject> subjectByCode = new HashMap<>();
        for (String code : subjectCodes) subjects.findBySchoolIdAndCode(TenantContext.get(), code).ifPresent(s -> subjectByCode.put(code, s));
        Map<String, Integer> coefficients = effectiveCoefficients(studentId, period.getAcademicSessionId());
        for (String subjectCode : subjectCodes) {
            List<AcademicCalculationEngine.ChildInput> childInputs = new ArrayList<>();
            List<PeriodMarkView> periodMarks = new ArrayList<>();
            List<AssessmentEvidenceView> evidence = new ArrayList<>();
            for (DependencyRow dependency : dependencies) {
                Calculation child = children.get(dependency.childCode().toUpperCase(Locale.ROOT));
                BulletinLineView childLine = child == null ? null : child.lines().stream()
                        .filter(line -> line.subjectCode().equalsIgnoreCase(subjectCode)).findFirst().orElse(null);
                BigDecimal mark = childLine == null ? null : childLine.mark();
                periodMarks.add(new PeriodMarkView(dependency.childCode(), mark));
                if (childLine != null && childLine.assessments() != null) evidence.addAll(childLine.assessments());
                List<String> childBlockers = child == null ? List.of("DEPENDENCY_MISSING") : subjectBlockers(child, subjectCode, childPeriodCode(dependency));
                if (childLine == null) childBlockers = appendDistinct(childBlockers, "MISSING");
                AcademicCalculationEngine.Product childProduct = child == null
                        ? (product == AcademicCalculationEngine.Product.ANNUAL ? AcademicCalculationEngine.Product.TERM : AcademicCalculationEngine.Product.SEQUENCE)
                        : productForPeriodType(childPeriodType(dependency));
                AcademicCalculationEngine.Result childResult = new AcademicCalculationEngine.Result(childProduct, mark,
                        mark == null ? BigDecimal.ZERO : BigDecimal.ONE, childBlockers, List.of(dependency.childCode()));
                childInputs.add(new AcademicCalculationEngine.ChildInput(dependency.childCode(), childResult,
                        dependency.weight(), dependency.optional()));
            }
            AcademicCalculationEngine.Result result;
            try {
                result = AcademicCalculationEngine.aggregate(product, childInputs);
            } catch (IllegalArgumentException ex) {
                result = new AcademicCalculationEngine.Result(product, null, BigDecimal.ZERO,
                        List.of("DEPENDENCY_MISSING"), List.of());
            }
            for (String resultBlocker : result.blockers()) {
                addDistinct(blockers, subjectCode + ":" + resultBlocker);
                issues.add(issueForBlocker(subjectCode + ":" + resultBlocker, period.getCode(),
                        lines, dependencies));
            }
            BigDecimal mark = result.exempt() ? null : result.value();
            Subject subject = subjectByCode.get(subjectCode);
            int coefficient = coefficients.getOrDefault(subjectCode, subject == null ? 1 : subject.getCoef());
            CurriculumMetadata metadata = curriculumMetadata(studentId, period.getAcademicSessionId(), subjectCode);
            lines.add(new BulletinLineView(subjectCode, subjectLabel(subject, subjectCode), coefficient, mark,
                    mark == null ? null : mark.multiply(BigDecimal.valueOf(coefficient)), null, appreciation(mark), uniqueEvidence(evidence),
                    periodMarks, metadata.teacherName(), metadata.groupCode(), metadata.groupLabel()));
        }
        if (lines.isEmpty()) addDistinct(blockers, "NO_SUBJECT_RESULT");
        String sourceHash = sourceHash(new CurrentSourceFingerprint(period.getId(), period.getVersion(), period.getPeriodType(),
                lines, blockers, sourceTraces, packetTraces));
        return finishWith(lines, blockers, issues, studentId, period, readinessRows, sourceTraces, packetTraces,
                sourceHash, readiness(blockers, issues));
    }

    private List<DependencyRow> dependencies(AcademicReportingPeriod period, CalculationContext context) {
        return context.dependencies.computeIfAbsent(period.getId(), ignored -> jdbc.query("""
                SELECT d.parent_period_id,d.child_period_id,child.code,child.label,child.period_type,
                       child.version,d.weight,d.optional,d.display_order
                  FROM academic_reporting_period_dependency d
                  JOIN academic_reporting_period child ON child.id=d.child_period_id
                 WHERE d.school_id=? AND d.academic_session_id=? AND d.parent_period_id=?
                 ORDER BY d.display_order,child.code
                """, (rs, n) -> new DependencyRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6),
                        rs.getBigDecimal(7), rs.getBoolean(8), rs.getInt(9)),
                TenantContext.get(), period.getAcademicSessionId(), period.getId()));
    }

    /** Authoritative sequence calculation backed by the pure status/normalisation engine. */
    private Calculation calculateSequence(UUID studentId, AcademicReportingPeriod period) {
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        UUID classId = enrollment == null ? null : enrollment.getSchoolClassId();
        List<AcademicAssessment> definition = classId == null
                ? assessments.findBySchoolIdAndReportingPeriodIdOrderByDisplayOrder(TenantContext.get(), period.getId())
                : assessments.findApplicableForClass(TenantContext.get(), period.getId(), classId);
        List<AcademicGrade> recorded = grades.findBySchoolIdAndStudentIdAndReportingPeriodIdOrderBySubjectCodeAscAssessmentIdAsc(
                TenantContext.get(), studentId, period.getId());
        Map<String, List<AcademicGrade>> bySubject = new LinkedHashMap<>();
        recorded.forEach(g -> bySubject.computeIfAbsent(g.getSubjectCode(), ignored -> new ArrayList<>()).add(g));
        LinkedHashSet<String> subjectCodes = new LinkedHashSet<>(bySubject.keySet());
        if (classId != null) {
            jdbc.query("SELECT s.code FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id "
                            + "WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? "
                            + "AND (c.active_from IS NULL OR c.active_from<=?) AND (c.active_to IS NULL OR c.active_to>=?) "
                            + "ORDER BY c.display_order,s.code",
                    rs -> { while (rs.next()) subjectCodes.add(rs.getString(1)); return null; },
                    TenantContext.get(), period.getAcademicSessionId(), classId, period.getStartDate(), period.getEndDate());
        }

        List<BulletinLineView> lines = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Map<String, Integer> coefficients = effectiveCoefficients(studentId, period.getAcademicSessionId());
        boolean secondaryClass = enrollment != null && "secondary".equalsIgnoreCase(enrollment.getLevelSnapshot());
        String competencyLocale = enrollment != null && "EN".equalsIgnoreCase(enrollment.getSubsystemSnapshot()) ? "en" : "fr";
        for (String subjectCode : subjectCodes) {
            List<AcademicAssessment> applicable = definition.stream()
                    .filter(a -> a.getSubjectCode() == null || a.getSubjectCode().equalsIgnoreCase(subjectCode))
                    .sorted(Comparator.comparingInt(AcademicAssessment::getDisplayOrder).thenComparing(AcademicAssessment::getCode))
                    .toList();
            Map<UUID, AcademicGrade> gradesByAssessment = bySubject.getOrDefault(subjectCode, List.of()).stream()
                    .collect(Collectors.toMap(AcademicGrade::getAssessmentId, g -> g, (first, last) -> last));
            List<AcademicCalculationEngine.AssessmentInput> inputs = new ArrayList<>();
            List<AssessmentEvidenceView> evidence = new ArrayList<>();
            List<AssessmentEvidenceView> secondaryEvidence = secondaryClass
                    ? secondaryCompetencyEvidence(studentId, period, classId, subjectCode, competencyLocale)
                    : List.of();
            if (secondaryClass && applicable.isEmpty()) {
                if (!secondaryEvidence.isEmpty()) {
                    // Secondary classes use the published, versioned competency model
                    // as their evidence source.  The primary APC assessment catalog is
                    // deliberately not substituted or silently mixed into this path.
                    evidence.addAll(secondaryEvidence);
                    for (AssessmentEvidenceView competency : secondaryEvidence) {
                        String status = competency.status() == null || competency.status().isBlank()
                                ? "MISSING" : competency.status().trim().toUpperCase(Locale.ROOT);
                        AcademicCalculationEngine.MarkStatus engineStatus = switch (status) {
                            case "SCORED" -> AcademicCalculationEngine.MarkStatus.SCORED;
                            case "ABSENT" -> AcademicCalculationEngine.MarkStatus.ABSENT;
                            case "EXEMPT" -> AcademicCalculationEngine.MarkStatus.EXEMPT;
                            default -> AcademicCalculationEngine.MarkStatus.MISSING;
                        };
                        inputs.add(new AcademicCalculationEngine.AssessmentInput(competency.mark(),
                                competency.maxScore(), competency.weight(), engineStatus));
                    }
                } else {
                    addDistinct(blockers, subjectCode + ":SECONDARY_COMPETENCY_MODEL_MISSING");
                }
            } else for (AcademicAssessment assessment : applicable) {
                    AcademicGrade grade = gradesByAssessment.get(assessment.getId());
                    String status = grade == null || grade.getValueStatus() == null || grade.getValueStatus().isBlank()
                            ? "MISSING" : grade.getValueStatus().trim().toUpperCase(Locale.ROOT);
                    if (grade == null && !assessment.isMandatory()) continue;
                    if (grade != null && "MISSING".equals(status) && !assessment.isMandatory()) continue;
                    AcademicCalculationEngine.MarkStatus engineStatus = switch (status) {
                        case "SCORED" -> AcademicCalculationEngine.MarkStatus.SCORED;
                        case "ABSENT" -> AcademicCalculationEngine.MarkStatus.ABSENT;
                        case "EXEMPT" -> AcademicCalculationEngine.MarkStatus.EXEMPT;
                        default -> AcademicCalculationEngine.MarkStatus.MISSING;
                    };
                    evidence.add(new AssessmentEvidenceView(assessment.getCode(), assessment.getLabel(),
                            grade == null ? null : grade.getMark(), assessment.getMaxScore(), assessment.getWeight(), status));
                    inputs.add(new AcademicCalculationEngine.AssessmentInput(grade == null ? null : grade.getMark(),
                            assessment.getMaxScore(), assessment.getWeight(), engineStatus));
            }
            if (applicable.isEmpty() && secondaryEvidence.isEmpty()) addDistinct(blockers, subjectCode + ":ASSESSMENT_NOT_CONFIGURED");
            AcademicCalculationEngine.Result result = AcademicCalculationEngine.sequence(inputs);
            result.blockers().forEach(blocker -> addDistinct(blockers, subjectCode + ":" + blocker));
            BigDecimal mark = result.exempt() ? null : result.value();
            Subject subject = subjects.findBySchoolIdAndCode(TenantContext.get(), subjectCode).orElse(null);
            int coefficient = coefficients.getOrDefault(subjectCode, subject == null ? 1 : subject.getCoef());
            SubjectResultComment comment = comments.findTopBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCodeOrderByUpdatedAtDesc(
                    TenantContext.get(), studentId, period.getId(), subjectCode).orElse(null);
            CurriculumMetadata metadata = curriculumMetadata(studentId, period.getAcademicSessionId(), subjectCode);
            String teacherRemark = comment == null ? null : comment.getComment();
            if (metadata.remarkRequired() && (teacherRemark == null || teacherRemark.isBlank())) {
                addDistinct(blockers, subjectCode + ":REMARK_REQUIRED");
            }
            lines.add(new BulletinLineView(subjectCode, subjectLabel(subject, subjectCode), coefficient, mark,
                    mark == null ? null : mark.multiply(BigDecimal.valueOf(coefficient)), teacherRemark,
                    appreciation(mark), evidence, List.of(new PeriodMarkView(period.getCode(), mark)),
                    metadata.teacherName(), metadata.groupCode(), metadata.groupLabel()));
        }
        if (definition.isEmpty() && !secondaryClass) blockers.add("ASSESSMENT_DEFINITIONS_MISSING");
        if (secondaryClass && lines.stream().allMatch(line -> line.assessments() == null || line.assessments().isEmpty())) {
            blockers.add("SECONDARY_COMPETENCY_MODEL_MISSING");
        }
        if (lines.isEmpty()) blockers.add("NO_SUBJECT_RESULT");
        return finish(lines, blockers, studentId, period);
    }

    /**
     * Resolve the highest published secondary competency model for one class,
     * subject, locale, and sequence period.  The mark row is optional so a
     * missing mark is retained as immutable evidence instead of disappearing.
     */
    private List<AssessmentEvidenceView> secondaryCompetencyEvidence(UUID studentId,
                                                                      AcademicReportingPeriod period,
                                                                      UUID classId,
                                                                      String subjectCode,
                                                                      String locale) {
        if (classId == null) return List.of();
        return jdbc.query("""
                SELECT c.code,c.description,mk.mark,c.max_score,
                       coalesce(mk.value_status,'MISSING')
                  FROM secondary_competency_model model
                  JOIN subject s ON s.id=model.subject_id
                  JOIN secondary_competency c ON c.model_id=model.id AND c.active
                  LEFT JOIN secondary_competency_mark mk
                    ON mk.model_id=model.id AND mk.competency_id=c.id
                   AND mk.reporting_period_id=model.reporting_period_id
                   AND mk.student_id=? AND mk.school_id=model.school_id
                 WHERE model.id=(
                       SELECT latest.id
                         FROM secondary_competency_model latest
                         JOIN subject latest_subject ON latest_subject.id=latest.subject_id
                        WHERE latest.school_id=? AND latest.reporting_period_id=?
                          AND latest.class_id=? AND latest.locale=?
                          AND latest.status='PUBLISHED'
                          AND upper(latest_subject.code)=upper(?)
                        ORDER BY latest.version DESC, latest.created_at DESC
                        LIMIT 1)
                 ORDER BY c.display_order,c.code
                """, (rs, n) -> new AssessmentEvidenceView(rs.getString(1), rs.getString(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4), BigDecimal.ONE, rs.getString(5)),
                studentId, TenantContext.get(), period.getId(), classId, locale, subjectCode);
    }

    private static List<AssessmentEvidenceView> uniqueEvidence(List<AssessmentEvidenceView> evidence) {
        if (evidence == null || evidence.isEmpty()) return List.of();
        Map<String, AssessmentEvidenceView> unique = new LinkedHashMap<>();
        for (AssessmentEvidenceView value : evidence) {
            if (value == null) continue;
            unique.putIfAbsent((value.code() == null ? "" : value.code()).toUpperCase(Locale.ROOT), value);
        }
        return List.copyOf(unique.values());
    }

    private Calculation finish(List<BulletinLineView> lines, List<String> blockers, UUID studentId, AcademicReportingPeriod period) {
        BigDecimal weighted = BigDecimal.ZERO, coefs = BigDecimal.ZERO;
        for (BulletinLineView l : lines) {
            if (l.mark() == null || l.weighted() == null) continue;
            weighted = weighted.add(l.weighted());
            coefs = coefs.add(BigDecimal.valueOf(l.coefficient()));
        }
        BigDecimal average = coefs.signum() == 0 ? null : weighted.divide(coefs, AcademicCalculationEngine.CALCULATION_SCALE, RoundingMode.HALF_UP);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow();
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        List<StudentEnrollment> classRoster = enrollment == null || enrollment.getSchoolClassId() == null ? List.of() : enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(TenantContext.get(), period.getAcademicSessionId(), enrollment.getSchoolClassId(), "ACTIVE");
        int rank = 1; int classSize = classRoster.size();
        return new Calculation(lines, blockers, average, rank, classSize,
                enrollment == null ? null : enrollment.getLevelSnapshot(),
                enrollment == null ? null : enrollment.getSubsystemSnapshot(),
                enrollment == null ? null : enrollment.getClassNameSnapshot(), null);
    }

    private Calculation finishWith(List<BulletinLineView> lines, List<String> blockers,
                                   List<BulletinIssueView> issues, UUID studentId,
                                   AcademicReportingPeriod period,
                                   List<DependencyReadinessView> dependencies,
                                   List<DependencySourceTrace> dependencySources,
                                   List<PacketTrace> packetTraces, String sourceHash,
                                   String inputReadiness) {
        Calculation base = finish(lines, blockers, studentId, period);
        return new Calculation(base.lines(), base.blockers(), base.average(), base.rank(), base.classSize(),
                base.educationalLevel(), base.subsystem(), base.className(), base.classStats(), inputReadiness,
                dependencies, issues, dependencySources, packetTraces, sourceHash);
    }

    private Calculation rebuild(Calculation base, List<String> blockers, List<BulletinIssueView> issues,
                                List<DependencyReadinessView> dependencies,
                                List<DependencySourceTrace> dependencySources, List<PacketTrace> packetTraces,
                                String sourceHash, String inputReadiness) {
        return new Calculation(base.lines(), blockers, base.average(), base.rank(), base.classSize(),
                base.educationalLevel(), base.subsystem(), base.className(), base.classStats(), inputReadiness,
                dependencies, issues, dependencySources, packetTraces, sourceHash);
    }

    private Calculation cycleCalculation(UUID studentId, AcademicReportingPeriod period) {
        String messageFr = period.getCode() + " : la dependance des periodes forme un cycle.";
        String messageEn = period.getCode() + ": the reporting-period dependency graph contains a cycle.";
        return finishWith(List.of(), List.of("DEPENDENCY_CYCLE"),
                List.of(issue("DEPENDENCY_CYCLE", "ERROR", period.getCode(), null, messageFr, messageEn, "academic-sessions")),
                studentId, period, List.of(), List.of(), List.of(), null, "BLOCKED");
    }

    private PacketReadiness packetReadiness(AcademicReportingPeriod period, UUID classId,
                                            List<String> fallbackSubjects, CalculationContext context) {
        String key = String.valueOf(classId) + ":" + period.getId();
        PacketReadiness cached = context.packetReadiness.get(key);
        if (cached != null) return cached;
        LinkedHashSet<String> expected = curriculumSubjectCodesForPeriod(period, classId);
        if (expected.isEmpty()) fallbackSubjects.forEach(expected::add);
        Map<String, PacketTrace> packetsBySubject = new HashMap<>();
        if (classId != null) {
            jdbc.query("""
                    SELECT id,subject_code,status,version,teacher_id,responsible_assignment_id,
                           responsible_assignment_version,submitted_at,reviewed_at
                      FROM academic_grade_packet
                     WHERE school_id=? AND academic_session_id=? AND reporting_period_id=? AND class_id=?
                    """, rs -> {
                while (rs.next()) {
                    packetsBySubject.put(rs.getString("subject_code").toUpperCase(Locale.ROOT),
                            new PacketTrace(rs.getObject("id", UUID.class), classId, period.getId(), period.getCode(),
                                    rs.getString("subject_code"), rs.getString("status"), rs.getLong("version"),
                                    rs.getObject("teacher_id", UUID.class), rs.getObject("responsible_assignment_id", UUID.class),
                                    rs.getObject("responsible_assignment_version", Long.class), instant(rs, "submitted_at"), instant(rs, "reviewed_at")));
                }
                return null;
            }, TenantContext.get(), period.getAcademicSessionId(), period.getId(), classId);
        }
        List<PacketTrace> traces = new ArrayList<>();
        List<BulletinIssueView> issues = new ArrayList<>();
        int accepted = 0, locked = 0, submitted = 0, draft = 0, returned = 0, missing = 0;
        String readiness = "READY";
        for (String subject : expected) {
            PacketTrace packet = packetsBySubject.get(subject.toUpperCase(Locale.ROOT));
            String status = packet == null ? "MISSING" : packet.status();
            if (packet == null) {
                packet = new PacketTrace(null, classId, period.getId(), period.getCode(), subject, status, 0,
                        null, null, null, null, null);
            }
            traces.add(packet);
            switch (status == null ? "MISSING" : status.toUpperCase(Locale.ROOT)) {
                case "ACCEPTED" -> accepted++;
                case "LOCKED" -> locked++;
                case "SUBMITTED" -> submitted++;
                case "DRAFT" -> draft++;
                case "RETURNED" -> returned++;
                default -> missing++;
            }
            if (Set.of("MISSING", "RETURNED").contains(status == null ? "MISSING" : status.toUpperCase(Locale.ROOT))) readiness = "BLOCKED";
            else if (Set.of("DRAFT", "SUBMITTED").contains(status.toUpperCase(Locale.ROOT)) && !"BLOCKED".equals(readiness)) readiness = "PROVISIONAL";
            if (!"ACCEPTED".equalsIgnoreCase(status) && !"LOCKED".equalsIgnoreCase(status)) {
                issues.add(packetIssue(period, subject, status));
            }
        }
        DependencyReadinessView row = new DependencyReadinessView(period.getId(), period.getCode(), period.getLabel(),
                period.getPeriodType(), BigDecimal.ONE, false, readiness, expected.size(), accepted, locked,
                submitted, draft, returned, missing);
        PacketReadiness result = new PacketReadiness(List.copyOf(traces), List.copyOf(issues), List.of(row), readiness);
        context.packetReadiness.put(key, result);
        return result;
    }

    private BulletinIssueView packetIssue(AcademicReportingPeriod period, String subjectCode, String status) {
        String normalized = status == null ? "MISSING" : status.toUpperCase(Locale.ROOT);
        String subject = subjectCode == null ? "" : subjectCode.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DRAFT" -> issue("GRADE_PACKET_DRAFT", "WARNING", period.getCode(), subject,
                    period.getCode() + " — " + subject + " : les notes sont enregistrees mais n'ont pas ete envoyees a la direction.",
                    period.getCode() + " — " + subject + ": grades are saved but have not been sent to management.", "grade-entry");
            case "SUBMITTED" -> issue("GRADE_PACKET_SUBMITTED", "WARNING", period.getCode(), subject,
                    period.getCode() + " — " + subject + " : les notes attendent la verification de la direction.",
                    period.getCode() + " — " + subject + ": grades are waiting for management review.", "grade-entry");
            case "RETURNED" -> issue("GRADE_PACKET_RETURNED", "ERROR", period.getCode(), subject,
                    period.getCode() + " — " + subject + " : la feuille a ete retournee pour correction.",
                    period.getCode() + " — " + subject + ": the grade sheet was returned for correction.", "grade-entry");
            default -> issue("GRADE_PACKET_MISSING", "ERROR", period.getCode(), subject,
                    period.getCode() + " — " + subject + " : aucune feuille de notes n'existe pour cette classe.",
                    period.getCode() + " — " + subject + ": no grade packet exists for this class.", "grade-entry");
        };
    }

    private DependencyReadinessView readinessFor(DependencyRow dependency, Calculation child,
                                                 AcademicReportingPeriod childPeriod) {
        int expected = child.dependencies().stream().mapToInt(DependencyReadinessView::expectedPacketCount).sum();
        int accepted = child.dependencies().stream().mapToInt(DependencyReadinessView::acceptedPacketCount).sum();
        int locked = child.dependencies().stream().mapToInt(DependencyReadinessView::lockedPacketCount).sum();
        int submitted = child.dependencies().stream().mapToInt(DependencyReadinessView::submittedPacketCount).sum();
        int draft = child.dependencies().stream().mapToInt(DependencyReadinessView::draftPacketCount).sum();
        int returned = child.dependencies().stream().mapToInt(DependencyReadinessView::returnedPacketCount).sum();
        int missing = child.dependencies().stream().mapToInt(DependencyReadinessView::missingPacketCount).sum();
        return new DependencyReadinessView(childPeriod.getId(), childPeriod.getCode(), childPeriod.getLabel(),
                childPeriod.getPeriodType(), dependency.weight(), dependency.optional(), child.inputReadiness(),
                expected, accepted, locked, submitted, draft, returned, missing);
    }

    private LinkedHashSet<String> curriculumSubjectCodes(UUID studentId, AcademicReportingPeriod period, UUID classId) {
        LinkedHashSet<String> codes = curriculumSubjectCodesForPeriod(period, classId);
        if (codes.isEmpty()) return codes;
        return codes;
    }

    private LinkedHashSet<String> curriculumSubjectCodesForPeriod(AcademicReportingPeriod period, UUID classId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (classId == null) return result;
        jdbc.query("""
                SELECT s.code
                  FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                   AND (c.active_from IS NULL OR c.active_from<=?)
                   AND (c.active_to IS NULL OR c.active_to>=?)
                 ORDER BY c.display_order,s.code
                """, rs -> { while (rs.next()) result.add(rs.getString(1)); return null; },
                TenantContext.get(), period.getAcademicSessionId(), classId, period.getStartDate(), period.getEndDate());
        return result;
    }

    private List<String> subjectBlockers(Calculation child, String subjectCode, String childPeriodCode) {
        return child.blockers().stream().filter(blocker -> {
            String upper = blocker.toUpperCase(Locale.ROOT);
            String subject = subjectCode.toUpperCase(Locale.ROOT);
            return upper.equals(subject) || upper.startsWith(subject + ":")
                    || upper.startsWith(childPeriodCode.toUpperCase(Locale.ROOT) + ":" + subject + ":")
                    || !upper.contains(":");
        }).toList();
    }

    private List<String> appendDistinct(List<String> values, String value) {
        List<String> result = new ArrayList<>(values == null ? List.of() : values);
        addDistinct(result, value);
        return result;
    }

    private String packetBlocker(String periodCode, String subjectCode, String code) {
        return periodCode + ":" + (subjectCode == null ? "" : subjectCode) + ":" + code;
    }

    private BulletinIssueView issueForBlocker(String blocker, String periodCode, List<BulletinLineView> lines) {
        String raw = blocker == null ? "" : blocker;
        String[] parts = raw.split(":");
        String code = parts.length == 0 ? raw : parts[parts.length - 1];
        String subject = parts.length > 1 && !parts[0].equalsIgnoreCase(periodCode) ? parts[0]
                : parts.length > 2 ? parts[1] : null;
        String subjectLabel = lines == null ? subject : lines.stream().filter(line -> subject != null && line.subjectCode().equalsIgnoreCase(subject))
                .map(BulletinLineView::subjectLabel).findFirst().orElse(subject);
        return issueForCode(code, periodCode, subject, subjectLabel);
    }

    private BulletinIssueView issueForBlocker(String blocker, String periodCode,
                                              List<BulletinLineView> lines, List<DependencyRow> ignored) {
        return issueForBlocker(blocker, periodCode, lines);
    }

    private BulletinIssueView issueForCode(String code, String periodCode, String subjectCode, String subjectLabel) {
        String subject = subjectLabel == null ? (subjectCode == null ? "" : subjectCode) : subjectLabel;
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "MISSING" -> issue("MISSING", "ERROR", periodCode, subjectCode,
                    periodCode + " — " + subject + " : une note obligatoire est manquante.",
                    periodCode + " — " + subject + ": a required mark is missing.", "grade-entry");
            case "ABSENT" -> issue("ABSENT", "ERROR", periodCode, subjectCode,
                    periodCode + " — " + subject + " : l'absence doit recevoir le traitement academique configure.",
                    periodCode + " — " + subject + ": the absence needs its configured academic treatment.", "grade-entry");
            case "REMARK_REQUIRED" -> issue("REMARK_REQUIRED", "ERROR", periodCode, subjectCode,
                    periodCode + " — " + subject + " : l'appreciation obligatoire est vide.",
                    periodCode + " — " + subject + ": the required subject remark is empty.", "grade-entry");
            case "ASSESSMENT_NOT_CONFIGURED" -> issue("ASSESSMENT_NOT_CONFIGURED", "ERROR", periodCode, subjectCode,
                    periodCode + " — " + subject + " : aucune evaluation applicable n'est configuree.",
                    periodCode + " — " + subject + ": no applicable assessment is configured.", "assessment-configuration");
            case "DEPENDENCY_CYCLE" -> issue("DEPENDENCY_CYCLE", "ERROR", periodCode, subjectCode,
                    periodCode + " : le graphe des dependances contient un cycle.",
                    periodCode + ": the dependency graph contains a cycle.", "academic-sessions");
            default -> issue(code, "ERROR", periodCode, subjectCode, periodCode + " : " + code,
                    periodCode + ": " + code, "academic");
        };
    }

    private BulletinIssueView issue(String code, String severity, String periodCode, String subjectCode,
                                    String messageFr, String messageEn, String repairTarget) {
        return new BulletinIssueView(code, severity, periodCode, subjectCode, messageFr, messageEn, repairTarget);
    }

    private String readiness(List<String> blockers) { return readiness(blockers, List.of()); }
    private String readiness(List<String> blockers, List<BulletinIssueView> issues) {
        boolean provisional = issues.stream().anyMatch(issue -> Set.of("GRADE_PACKET_DRAFT", "GRADE_PACKET_SUBMITTED").contains(issue.code()))
                || blockers.stream().anyMatch(blocker -> blocker.endsWith(":GRADE_PACKET_DRAFT") || blocker.endsWith(":GRADE_PACKET_SUBMITTED"));
        boolean blocked = blockers.stream().anyMatch(blocker -> !blocker.endsWith(":GRADE_PACKET_DRAFT") && !blocker.endsWith(":GRADE_PACKET_SUBMITTED"));
        return blocked ? "BLOCKED" : provisional ? "PROVISIONAL" : "READY";
    }

    private AcademicCalculationEngine.Product product(AcademicReportingPeriod period) {
        return "ANNUAL_RESULT".equals(period.getPeriodType()) ? AcademicCalculationEngine.Product.ANNUAL : AcademicCalculationEngine.Product.TERM;
    }

    private AcademicCalculationEngine.Product productForPeriodType(String periodType) {
        return "ANNUAL_RESULT".equals(periodType) ? AcademicCalculationEngine.Product.ANNUAL
                : "TERM_RESULT".equals(periodType) ? AcademicCalculationEngine.Product.TERM
                : AcademicCalculationEngine.Product.SEQUENCE;
    }

    private String sourceKind(AcademicReportingPeriod period) {
        return "SEQUENCE".equals(period.getPeriodType()) ? "LIVE_SEQUENCE" : "LIVE_TERM";
    }

    private Calculation calculationFromSnapshot(BulletinVersion version) {
        SnapshotPayload payload = readPayload(version);
        SnapshotTrace trace = payload.trace();
        List<String> blockers = payload.blockers() == null ? List.of() : payload.blockers();
        return new Calculation(payload.lines() == null ? List.of() : payload.lines(), blockers,
                payload.average(), payload.rank(), payload.classSize(), payload.educationalLevel(),
                payload.subsystem(), payload.className(), payload.classStats(),
                blockers.isEmpty() ? "READY" : "BLOCKED", List.of(),
                payload.issues() == null ? List.of() : payload.issues(),
                trace == null ? List.of() : trace.dependencySources(),
                trace == null ? List.of() : trace.packetTraces(),
                trace == null ? version.getSnapshotHash() : trace.sourceHash());
    }

    private String sourceHash(Object source) {
        try { return sha256(mapper.writeValueAsString(source)); }
        catch (JsonProcessingException ex) { throw ApiException.conflict("Impossible de fingerprint la source academique"); }
    }

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private AcademicReportingPeriod childPeriod(DependencyRow dependency) {
        return periods.findByIdAndSchoolId(dependency.childPeriodId(), TenantContext.get()).orElse(null);
    }

    private String childPeriodCode(DependencyRow dependency) { return dependency.childCode(); }
    private String childPeriodType(DependencyRow dependency) { return dependency.childPeriodType(); }

    /** Calculate class statistics only for the requested product, reusing raw peer calculations. */

    /** Calculate class statistics from the same server-side formula as the student. */
    private Calculation withClassStatistics(UUID studentId, AcademicReportingPeriod period, Calculation own) {
        return withClassStatistics(studentId, period, own, new CalculationContext());
    }

    private Calculation withClassStatistics(UUID studentId, AcademicReportingPeriod period,
                                             Calculation own, CalculationContext context) {
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        if (enrollment == null || enrollment.getSchoolClassId() == null) return own;
        List<StudentEnrollment> roster = enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                TenantContext.get(), period.getAcademicSessionId(), enrollment.getSchoolClassId(), "ACTIVE");
        List<Calculation> eligible = new ArrayList<>();
        for (StudentEnrollment peer : roster) {
            Calculation c = calculateCurrent(peer.getStudentId(), period, context);
            if (c.blockers().isEmpty()) eligible.add(c);
        }
        if (eligible.isEmpty()) return copyWithStats(own, null, roster.size(),
                new ClassStatsView(null, null, null, 0, null, 0));
        List<BigDecimal> averages = eligible.stream().map(Calculation::average).filter(Objects::nonNull).sorted().toList();
        if (averages.isEmpty()) return copyWithStats(own, null, roster.size(),
                new ClassStatsView(null, null, null, 0, null, 0));
        BigDecimal sum = averages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal classAverage = sum.divide(BigDecimal.valueOf(averages.size()), 12, RoundingMode.HALF_UP);
        BigDecimal minimum = averages.get(0), maximum = averages.get(averages.size() - 1);
        int successCount = (int) averages.stream().filter(x -> x.compareTo(BigDecimal.TEN) >= 0).count();
        BigDecimal successRate = BigDecimal.valueOf(successCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(averages.size()), 2, RoundingMode.HALF_UP);
        Integer rank = own.blockers().isEmpty() && own.average() != null ? 1 + (int) averages.stream().filter(x -> x.compareTo(own.average()) > 0).count() : null;
        ClassStatsView stats = new ClassStatsView(classAverage, minimum, maximum, successCount, successRate, averages.size());
        return copyWithStats(own, rank, roster.size(), stats);
    }

    private Calculation copyWithStats(Calculation source, Integer rank, int classSize, ClassStatsView stats) {
        return new Calculation(source.lines(), source.blockers(), source.average(), rank, classSize,
                source.educationalLevel(), source.subsystem(), source.className(), stats,
                source.inputReadiness(), source.dependencies(), source.issues(), source.dependencySources(),
                source.packetTraces(), source.sourceHash());
    }

    private AuthoritativeSnapshotView authoritativeSnapshot(AcademicReportingPeriod period, Student student,
                                                             StudentEnrollment enrollment, Calculation calculation,
                                                             AttendanceSummaryView attendance,
                                                             ConductSummaryView conduct, SnapshotTrace trace,
                                                             String canonicalHash) {
        return authoritativeSnapshot(period, student, enrollment, calculation, attendance, conduct, trace,
                canonicalHash, null, null);
    }

    private AuthoritativeSnapshotView authoritativeSnapshot(AcademicReportingPeriod period, Student student,
                                                             StudentEnrollment enrollment, Calculation calculation,
                                                             AttendanceSummaryView attendance,
                                                             ConductSummaryView conduct, SnapshotTrace trace,
                                                             String canonicalHash, UUID generationActorId,
                                                             Instant generationTime) {
        UUID school = TenantContext.get();
        DocumentDesignEvidenceView design = trace == null || trace.documentDesign() == null ? null : new DocumentDesignEvidenceView(
                trace.documentDesign().templateId(), trace.documentDesign().templateFamily(), trace.documentDesign().product(),
                trace.documentDesign().locale(), trace.documentDesign().templateVersion(), trace.documentDesign().templateHash(),
                trace.documentDesign().brandingId(), trace.documentDesign().brandingVersion(), trace.documentDesign().brandingHash(),
                trace.documentDesign().principalName(), trace.documentDesign().principalTitle(),
                trace.documentDesign().classMasterTitle(), trace.documentDesign().councilTitle(),
                trace.documentDesign().templateConfigJson());

        SnapshotGuardianView guardian = jdbc.query("""
                SELECT g.id,g.display_name,sg.relationship_type
                  FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id
                 WHERE sg.school_id=? AND sg.student_id=? AND sg.receives_academic=true
                   AND sg.effective_to IS NULL AND g.status<>'MERGED'
                 ORDER BY sg.legal_guardian DESC, sg.emergency_priority NULLS LAST, g.display_name
                 LIMIT 1
                """, rs -> rs.next() ? new SnapshotGuardianView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)) : null,
                school, student.getId());

        SnapshotTeacherView classMaster = jdbc.query("""
                SELECT a.employee_id,e.code,e.name,a.role,a.version
                  FROM class_teacher_assignment a JOIN employee e ON e.id=a.employee_id
                 WHERE a.school_id=? AND a.academic_session_id=? AND a.class_id=?
                   AND a.role='HOMEROOM' AND a.status='ACTIVE'
                   AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?)
                 ORDER BY a.effective_from DESC LIMIT 1
                """, rs -> rs.next() ? new SnapshotTeacherView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), null, rs.getLong(5)) : null,
                school, period.getAcademicSessionId(), enrollment.getSchoolClassId(), period.getEndDate(), period.getStartDate());
        List<SnapshotTeacherView> subjectTeachers = jdbc.query("""
                SELECT ast.employee_id,e.code,e.name,ast.role,s.code,ast.version
                  FROM academic_class_subject_teacher ast
                  JOIN employee e ON e.id=ast.employee_id JOIN subject s ON s.id=ast.subject_id
                 WHERE ast.school_id=? AND ast.academic_session_id=? AND ast.class_id=? AND ast.active
                   AND (ast.effective_from IS NULL OR ast.effective_from<=?)
                   AND (ast.effective_to IS NULL OR ast.effective_to>=?)
                 ORDER BY s.code,e.name
                """, (rs, n) -> new SnapshotTeacherView(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6)),
                school, period.getAcademicSessionId(), enrollment.getSchoolClassId(), period.getEndDate(), period.getStartDate());

        ProfileAssetTrace photoTrace = trace == null ? null : trace.profilePhoto();
        SnapshotPhotoView photo = photoTrace == null ? new SnapshotPhotoView(null, null, null, null, null, "INITIALS", null)
                : new SnapshotPhotoView(photoTrace.assetVersionId(), photoTrace.contentType(), photoTrace.sha256(),
                photoTrace.width(), photoTrace.height(), photoTrace.fallbackDecision(), photoTrace.capturedAt());

        SnapshotSchoolView schoolView = jdbc.query("""
                SELECT id,code,name,authority,address,city,country,phone,email,website
                  FROM school WHERE id=?
                """, rs -> rs.next() ? new SnapshotSchoolView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), design) : new SnapshotSchoolView(school, null, null, null,
                        null, null, null, null, null, null, design), school);

        SnapshotCurriculumView curriculum = curriculumSnapshot(period, enrollment, calculation);
        List<SnapshotSubjectResultView> subjectResults = (calculation == null ? List.<BulletinLineView>of() : calculation.lines()).stream()
                .map(line -> new SnapshotSubjectResultView(line.subjectCode(), line.subjectLabel(), line.coefficient(),
                        line.mark(), display(line.mark()), line.weighted(), display(line.weighted()),
                        line.mark() == null ? "EXEMPT_OR_MISSING" : "SCORED", line.teacherRemark(), line.appreciation(),
                        line.periodMarks(), line.assessments(), null, line.subjectGroupCode(), line.subjectGroupLabel())).toList();
        SnapshotResultView result = new SnapshotResultView(calculation == null ? null : calculation.average(),
                display(calculation == null ? null : calculation.average()), calculation == null ? null : calculation.rank(),
                calculation == null ? 0 : calculation.classSize(), subjectResults,
                calculation == null ? List.of() : groupStats(calculation.lines()),
                calculation == null ? null : calculation.classStats(),
                calculation == null ? List.of() : calculation.blockers(), calculation == null ? List.of() : calculation.issues());
        SnapshotTemplateView template = trace == null || trace.documentDesign() == null ? null : new SnapshotTemplateView(
                trace.documentDesign().templateId(), trace.documentDesign().templateFamily(), trace.documentDesign().product(),
                trace.documentDesign().locale(), trace.documentDesign().templateVersion(), trace.documentDesign().templateHash(),
                trace.documentDesign().brandingId(), trace.documentDesign().brandingVersion(), trace.documentDesign().brandingHash(),
                trace.documentDesign().templateConfigJson());

        List<SnapshotSourceVersionView> sources = sourceVersions(period, enrollment, trace, curriculum);
        return new AuthoritativeSnapshotView(1, school, period.getAcademicSessionId(), period.getId(), period.getCode(),
                period.getLabel(), productName(period),
                new SnapshotStudentView(student.getId(), student.getLastName() + " " + student.getFirstName(),
                        student.getFirstName(), student.getLastName(), student.getMatricule(), student.getDob(),
                        student.getBirthplace(), student.getSex(), student.isRepeats()),
                new SnapshotEnrollmentView(enrollment.getId(), enrollment.getSchoolClassId(), enrollment.getClassNameSnapshot(),
                        enrollment.getLevelSnapshot(), enrollment.getSubsystemSnapshot(), calculation == null ? 0 : calculation.classSize()),
                guardian, new SnapshotStaffView(classMaster, subjectTeachers), photo, schoolView, curriculum, result,
                evidence(trace), attendance, conduct, template, FORMULA_VERSION, period.getCalculationPolicy(),
                null, null, sources, canonicalHash);
    }

    private SnapshotCurriculumView curriculumSnapshot(AcademicReportingPeriod period, StudentEnrollment enrollment,
                                                       Calculation calculation) {
        UUID school = TenantContext.get();
        UUID classId = enrollment.getSchoolClassId();
        Map<String, Object> version = jdbc.query("""
                SELECT v.id,v.version_number,v.state,v.canonical_content_hash
                  FROM academic_curriculum_version v
                 WHERE v.school_id=? AND v.academic_session_id=? AND v.scope_type='CLASS' AND v.class_id=?
                   AND v.state='PUBLISHED'
                 ORDER BY v.version_number DESC LIMIT 1
                """, rs -> {
                    if (!rs.next()) return Map.of();
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", rs.getObject(1, UUID.class));
                    value.put("number", rs.getInt(2));
                    value.put("state", rs.getString(3));
                    value.put("hash", rs.getString(4));
                    return value;
                },
                school, period.getAcademicSessionId(), classId);
        UUID versionId = (UUID) version.get("id");
        List<SnapshotCurriculumRowView> rows = jdbc.query("""
                SELECT c.id,c.subject_id,s.code,coalesce(s.label->>'fr',s.label->>'en',s.code),
                       c.group_id,g.code,coalesce(g.label->>'fr',g.label->>'en',g.code),c.display_order,
                       c.coefficient,c.max_score,c.mandatory,c.pass_threshold,c.show_subject_rank,c.remark_required
                  FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
                  LEFT JOIN academic_subject_group g ON g.id=c.group_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                   AND (c.active_from IS NULL OR c.active_from<=?) AND (c.active_to IS NULL OR c.active_to>=?)
                 ORDER BY c.display_order,s.code
                """, (rs, n) -> new SnapshotCurriculumRowView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getInt(8), rs.getInt(9), rs.getBigDecimal(10), rs.getBoolean(11), rs.getBigDecimal(12),
                        rs.getBoolean(13), rs.getBoolean(14)), school, period.getAcademicSessionId(), classId,
                period.getStartDate(), period.getEndDate());
        return new SnapshotCurriculumView(versionId, versionId == null ? 0 : (Integer) version.get("number"),
                versionId == null ? "MISSING" : (String) version.get("state"), (String) version.get("hash"), rows);
    }

    private List<SnapshotSourceVersionView> sourceVersions(AcademicReportingPeriod period, StudentEnrollment enrollment,
                                                           SnapshotTrace trace, SnapshotCurriculumView curriculum) {
        List<SnapshotSourceVersionView> out = new ArrayList<>();
        out.add(new SnapshotSourceVersionView("REPORTING_PERIOD", period.getId(), period.getVersion(), null, period.getCode()));
        out.add(new SnapshotSourceVersionView("ENROLLMENT", enrollment.getId(), enrollment.getVersion(), null, enrollment.getClassNameSnapshot()));
        if (curriculum != null && curriculum.versionId() != null)
            out.add(new SnapshotSourceVersionView("CURRICULUM_VERSION", curriculum.versionId(), (long) curriculum.versionNumber(), curriculum.contentHash(), "curriculum"));
        if (trace != null) {
            trace.assessments().forEach(a -> out.add(new SnapshotSourceVersionView("ASSESSMENT", a.id(), a.version(), null, a.code())));
            trace.subjectAssignments().forEach(a -> out.add(new SnapshotSourceVersionView("TEACHER_ASSIGNMENT", a.id(), a.version(), null, a.subjectCode())));
            trace.homeroomAssignments().forEach(a -> out.add(new SnapshotSourceVersionView("CLASS_MASTER_ASSIGNMENT", a.id(), a.version(), null, a.role())));
            jdbc.query("""
                    SELECT id,version,subject_code
                      FROM subject_result_comment
                     WHERE school_id=? AND student_id=? AND reporting_period_id=?
                     ORDER BY subject_code
                    """, (rs, n) -> {
                        out.add(new SnapshotSourceVersionView("SUBJECT_COMMENT", rs.getObject(1, UUID.class),
                                rs.getLong(2), null, rs.getString(3)));
                        return null;
                    }, TenantContext.get(), enrollment.getStudentId(), period.getId());
            if (trace.profilePhoto() != null) out.add(new SnapshotSourceVersionView("PROFILE_PHOTO", trace.profilePhoto().assetVersionId(), null, trace.profilePhoto().sha256(), "profile photo"));
            if (trace.documentDesign() != null) {
                out.add(new SnapshotSourceVersionView("TEMPLATE", trace.documentDesign().templateId(), (long) trace.documentDesign().templateVersion(), trace.documentDesign().templateHash(), "template"));
                out.add(new SnapshotSourceVersionView("BRANDING", trace.documentDesign().brandingId(), (long) trace.documentDesign().brandingVersion(), trace.documentDesign().brandingHash(), "branding"));
            }
            trace.dependencySources().forEach(d -> out.add(new SnapshotSourceVersionView("DEPENDENCY", d.childPeriodId(), d.childPeriodVersion(), d.sourceHash(), d.childPeriodCode())));
            trace.packetTraces().forEach(p -> out.add(new SnapshotSourceVersionView("GRADE_PACKET", p.packetId(), p.version(), null, p.childReportingPeriodCode() + ":" + p.subjectCode())));
        }
        return out;
    }

    private void persistSourceIndex(UUID bulletinVersionId, CurrentSnapshot current) {
        SnapshotCurriculumView curriculum = current.trace() == null ? null
                : curriculumSnapshot(period(current.trace().reportingPeriodId()), current.enrollment(), current.calculation());
        List<SnapshotSourceVersionView> sources = sourceVersions(
                period(current.trace().reportingPeriodId()), current.enrollment(), current.trace(), curriculum);
        jdbc.batchUpdate("""
                INSERT INTO bulletin_snapshot_source_version
                    (school_id,bulletin_version_id,source_type,source_id,source_version,source_hash,source_label)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT DO NOTHING
                """, sources, sources.size(), (ps, source) -> {
            ps.setObject(1, TenantContext.get());
            ps.setObject(2, bulletinVersionId);
            ps.setString(3, source.sourceType());
            ps.setObject(4, source.sourceId());
            if (source.sourceVersion() == null) ps.setObject(5, null); else ps.setLong(5, source.sourceVersion());
            ps.setString(6, source.sourceHash());
            ps.setString(7, source.label());
        });
    }

    private static BigDecimal display(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record SnapshotDocument(String json, String canonicalHash) {}

    private SnapshotDocument writeSnapshot(AcademicReportingPeriod p, Student s, StudentEnrollment e, Calculation c,
                                 AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace) {
        try {
            UUID actor = currentUserId();
            Instant generatedAt = Instant.now();
            AuthoritativeSnapshotView contract = authoritativeSnapshot(p, s, e, c, attendance, conduct, trace,
                    null, null, null);
            SnapshotPayload draft = new SnapshotPayload(p.getCode(), p.getLabel(), p.getPeriodType(),
                    s.getId(), s.getMatricule(), s.getLastName() + " " + s.getFirstName(),
                    c.educationalLevel(), c.subsystem(), e.getClassNameSnapshot(), c.lines(), c.average(), c.rank(), c.classSize(), c.blockers(),
                    c.issues(), p.getCalculationPolicy(), attendance, conduct, c.classStats(), groupStats(c.lines()), trace,
                    contract, null);
            String canonicalHash = sha256(mapper.writeValueAsString(draft));
            AuthoritativeSnapshotView finalized = authoritativeSnapshot(p, s, e, c, attendance, conduct, trace,
                    canonicalHash, actor, generatedAt);
            String json = mapper.writeValueAsString(new SnapshotPayload(p.getCode(), p.getLabel(), p.getPeriodType(),
                    s.getId(), s.getMatricule(), s.getLastName() + " " + s.getFirstName(),
                    c.educationalLevel(), c.subsystem(), e.getClassNameSnapshot(), c.lines(), c.average(), c.rank(), c.classSize(), c.blockers(),
                    c.issues(), p.getCalculationPolicy(), attendance, conduct, c.classStats(), groupStats(c.lines()), trace,
                    finalized, canonicalHash));
            return new SnapshotDocument(json, canonicalHash);
        } catch (JsonProcessingException ex) {
            throw ApiException.conflict("Impossible de créer le snapshot du bulletin");
        }
    }
    private BulletinSnapshotView view(BulletinVersion v, AcademicReportingPeriod p, Student s, Calculation c,
                                      AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace) {
        String relation = v.getId() == null ? "PREVIEW" :
                Set.of("PUBLISHED", "VALIDATED").contains(v.getState()) ? "OFFICIAL" : "CURRENT";
        return view(v, p, s, c, attendance, conduct, trace, relation, false);
    }

    private BulletinSnapshotView view(BulletinVersion v, AcademicReportingPeriod p, Student s, Calculation c,
                                      AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace,
                                      String relation, boolean refreshRequired) {
        StudentEnrollment snapshotEnrollment = trace == null || trace.enrollmentId() == null ? null
                : enrollments.findById(trace.enrollmentId()).orElse(null);
        AuthoritativeSnapshotView snapshot = snapshotEnrollment == null ? null
                : authoritativeSnapshot(p, s, snapshotEnrollment, c, attendance, conduct, trace,
                v.getCanonicalSnapshotHash() == null ? v.getSnapshotHash() : v.getCanonicalSnapshotHash(),
                v.getGenerationActorId(), v.getGenerationTime());
        return view(v, p, s, c, attendance, conduct, trace, relation, refreshRequired, snapshot);
    }

    private BulletinSnapshotView view(BulletinVersion v, AcademicReportingPeriod p, Student s, Calculation c,
                                      AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace,
                                      String relation, boolean refreshRequired, AuthoritativeSnapshotView snapshot) {
        List<String> validationBlockers = officialBlockers(c, attendance, conduct, p.getPeriodType());
        List<BulletinIssueView> issues = new ArrayList<>(c.issues());
        if (validationBlockers.contains("CONDUCT_NOT_APPROVED")) {
            issues.add(issue("CONDUCT_NOT_APPROVED", "ERROR", p.getCode(), null,
                    "Le conseil de classe doit être approuvé avant validation.",
                    "The class council must be approved before validation.", "report-card-inputs"));
        }
        if ("STALE".equals(relation)) {
            issues.add(issue("BULLETIN_DRAFT_STALE", "WARNING", p.getCode(), null,
                    "Le brouillon durable est obsolète par rapport aux sources actuelles.",
                    "The durable draft is stale compared with the current sources.", "bulletin-refresh"));
        }
        boolean active = v.getId() != null && Set.of("DRAFT", "RETURNED").contains(v.getState());
        boolean current = "CURRENT".equals(relation);
        BulletinCapabilitiesView capabilities = new BulletinCapabilitiesView(
                v.getId() == null && validationBlockers.isEmpty(),
                active && refreshRequired && validationBlockers.isEmpty(),
                active && current && validationBlockers.isEmpty(),
                "VALIDATED".equals(v.getState()) && current && validationBlockers.isEmpty(),
                validationBlockers);
        BulletinWorkflowMetaView workflow = new BulletinWorkflowMetaView(
                c.inputReadiness(), relation, c.sourceHash(), v.getId(), v.getState(), v.getVersion(),
                v.getSnapshotHash(), v.getAverage(), refreshRequired, c.dependencies(), capabilities);
        String snapshotName = snapshot == null || snapshot.student() == null ? s.getLastName() + " " + s.getFirstName() : snapshot.student().name();
        String snapshotMatricule = snapshot == null || snapshot.student() == null ? s.getMatricule() : snapshot.student().matricule();
        String snapshotClassName = snapshot == null || snapshot.enrollment() == null ? c.className() : snapshot.enrollment().classLabel();
        String snapshotPeriodCode = snapshot == null ? p.getCode() : snapshot.reportingPeriodCode();
        String snapshotPeriodLabel = snapshot == null ? p.getLabel() : snapshot.reportingPeriodLabel();
        String snapshotProduct = snapshot == null ? productName(p) : snapshot.product();
        return new BulletinSnapshotView(v.getId(), p.getAcademicSessionId(), p.getId(), snapshotPeriodCode, snapshotPeriodLabel,
                s.getId(), snapshotName, snapshotMatricule, c.educationalLevel(), c.subsystem(), snapshotClassName, c.lines(),
                c.average(), c.rank(), c.classSize(), v.getState(), c.blockers().isEmpty(), c.blockers(),
                v.getSnapshotHash(), v.getCalculationPolicy(), v.getGeneralAppreciation(), attendance, conduct,
                v.getVersion(), c.classStats(), v.getSupersedesId(), v.getCorrectsBulletinVersionId(),
                v.getCorrectionReason(), v.getCorrectionRequestedBy(), v.getCorrectionRequestedAt(),
                groupStats(c.lines()), evidence(trace), p.getPeriodType(), snapshotProduct, workflow, issues, snapshot);
    }
    private BulletinSnapshotView viewFromSnapshot(BulletinVersion v, AcademicReportingPeriod p, Student s) {
        try {
            SnapshotPayload x = mapper.readValue(v.getSnapshotJson(), SnapshotPayload.class);
            List<BulletinLineView> lines = x.lines() == null ? List.of() : x.lines();
            List<String> blockers = x.blockers() == null ? List.of() : x.blockers();
            Calculation calculation = new Calculation(lines, blockers, x.average(), x.rank(), x.classSize(),
                    x.educationalLevel(), x.subsystem(), x.className(), x.classStats(),
                    blockers.isEmpty() ? "READY" : "BLOCKED", List.of(),
                    x.issues() == null ? List.of() : x.issues(), List.of(), List.of(),
                    x.trace() == null ? null : x.trace().sourceHash());
            String relation = Set.of("PUBLISHED", "VALIDATED").contains(v.getState()) ? "OFFICIAL" : "PERSISTED";
            return view(v, p, s, calculation, x.attendance(), x.conduct(), x.trace(), relation, false, x.snapshot());
        } catch (Exception ex) {
            throw ApiException.conflict("Snapshot de bulletin illisible");
        }
    }

    private String productName(AcademicReportingPeriod period) {
        return switch (period.getPeriodType()) {
            case "TERM_RESULT" -> "TERM";
            case "ANNUAL_RESULT" -> "ANNUAL";
            default -> "SEQUENCE";
        };
    }
    private record Calculation(List<BulletinLineView> lines, List<String> blockers, BigDecimal average,
                                Integer rank, int classSize, String educationalLevel, String subsystem,
                                String className, ClassStatsView classStats, String inputReadiness,
                                List<DependencyReadinessView> dependencies,
                                List<BulletinIssueView> issues,
                                List<DependencySourceTrace> dependencySources,
                                List<PacketTrace> packetTraces, String sourceHash) {
        private Calculation(List<BulletinLineView> lines, List<String> blockers, BigDecimal average,
                             Integer rank, int classSize, String educationalLevel, String subsystem,
                             String className, ClassStatsView classStats) {
            this(lines, blockers, average, rank, classSize, educationalLevel, subsystem, className,
                    classStats, "READY", List.of(), List.of(), List.of(), List.of(), null);
        }

        private Calculation {
            lines = lines == null ? List.of() : List.copyOf(lines);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            issues = issues == null ? List.of() : List.copyOf(issues);
            dependencySources = dependencySources == null ? List.of() : List.copyOf(dependencySources);
            packetTraces = packetTraces == null ? List.of() : List.copyOf(packetTraces);
        }
    }

    private record CalcKey(UUID studentId, UUID periodId) {}
    private static final class CalculationContext {
        private final Map<CalcKey, Calculation> calculations = new HashMap<>();
        private final Set<CalcKey> visiting = new HashSet<>();
        private final Map<UUID, List<DependencyRow>> dependencies = new HashMap<>();
        private final Map<String, PacketReadiness> packetReadiness = new HashMap<>();
    }

    private record CurrentSnapshot(Student student, StudentEnrollment enrollment, Calculation calculation,
                                   AttendanceSummaryView attendance, ConductSummaryView conduct,
                                   SnapshotTrace trace, String json, String hash) {}

    private record PacketReadiness(List<PacketTrace> traces, List<BulletinIssueView> issues,
                                   List<DependencyReadinessView> readinessRows, String readiness) {}

    private record CurrentSourceFingerprint(UUID periodId, long periodVersion, String periodType,
                                           List<BulletinLineView> lines, List<String> blockers,
                                           List<DependencySourceTrace> dependencySources,
                                           List<PacketTrace> packetTraces) {}

    private record DependencySourceTrace(UUID childPeriodId, String childPeriodCode, long childPeriodVersion,
                                         BigDecimal dependencyWeight, boolean optional, String sourceKind,
                                         String sourceHash, List<PacketTrace> packetTraces) {
        private DependencySourceTrace {
            packetTraces = packetTraces == null ? List.of() : List.copyOf(packetTraces);
        }
    }

    private record PacketTrace(UUID packetId, UUID classId, UUID childReportingPeriodId,
                               String childReportingPeriodCode, String subjectCode, String status,
                               long version, UUID teacherId, UUID responsibleAssignmentId,
                               Long responsibleAssignmentVersion, Instant submittedAt, Instant reviewedAt) {}
    private SnapshotTrace snapshotTrace(AcademicReportingPeriod period, Student student, StudentEnrollment enrollment) {
        UUID school = TenantContext.get();
        UUID classId = enrollment.getSchoolClassId();
        List<CurriculumTrace> curriculum = classId == null ? List.of() : jdbc.query("""
                SELECT c.id,s.code,c.coefficient,c.version
                  FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id
                 WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?
                   AND (c.active_from IS NULL OR c.active_from<=?) AND (c.active_to IS NULL OR c.active_to>=?)
                 ORDER BY c.display_order,s.code
                """, (rs,n) -> new CurriculumTrace(rs.getObject(1, UUID.class),rs.getString(2),rs.getInt(3),rs.getLong(4)),
                school, period.getAcademicSessionId(), classId, period.getStartDate(), period.getEndDate());
        List<AssessmentTrace> assessments = jdbc.query("""
                SELECT a.id,a.code,a.version,g.id,g.version,g.value_status,g.mark
                  FROM academic_assessment a
                  LEFT JOIN academic_grade g ON g.assessment_id=a.id AND g.student_id=? AND g.reporting_period_id=? AND g.school_id=?
                 WHERE a.school_id=? AND a.reporting_period_id=?
                   AND (a.class_id IS NULL OR a.class_id=?)
                 ORDER BY a.display_order,a.code
                """, (rs,n) -> new AssessmentTrace(rs.getObject(1, UUID.class),rs.getString(2),rs.getLong(3),
                        rs.getObject(4, UUID.class),rs.getObject(5, Long.class),rs.getString(6),rs.getBigDecimal(7)),
                student.getId(), period.getId(), school, school, period.getId(), classId);
        List<AssignmentTrace> subjectAssignments = classId == null ? List.of() : jdbc.query("""
                SELECT ast.id,ast.version,s.code,ast.employee_id,ast.role,ast.effective_from,ast.effective_to
                  FROM academic_class_subject_teacher ast JOIN subject s ON s.id=ast.subject_id
                 WHERE ast.school_id=? AND ast.academic_session_id=? AND ast.class_id=? AND ast.active
                   AND (ast.effective_from IS NULL OR ast.effective_from<=?) AND (ast.effective_to IS NULL OR ast.effective_to>=?)
                 ORDER BY s.code
                """, (rs,n) -> new AssignmentTrace(rs.getObject(1, UUID.class),rs.getLong(2),rs.getString(3),
                        rs.getObject(4, UUID.class),rs.getString(5),rs.getObject(6, java.time.LocalDate.class),rs.getObject(7, java.time.LocalDate.class)),
                school, period.getAcademicSessionId(), classId, period.getEndDate(), period.getStartDate());
        List<AssignmentTrace> homeroomAssignments = classId == null ? List.of() : jdbc.query("""
                SELECT a.id,a.version,NULL,a.employee_id,a.role,a.effective_from,a.effective_to
                  FROM class_teacher_assignment a
                 WHERE a.school_id=? AND a.academic_session_id=? AND a.class_id=? AND a.role='HOMEROOM' AND a.status='ACTIVE'
                   AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>=?)
                """, (rs,n) -> new AssignmentTrace(rs.getObject(1, UUID.class),rs.getLong(2),null,
                        rs.getObject(4, UUID.class),rs.getString(5),rs.getObject(6, java.time.LocalDate.class),rs.getObject(7, java.time.LocalDate.class)),
                school, period.getAcademicSessionId(), classId, period.getEndDate(), period.getStartDate());
        ProfileAssetTrace profilePhoto = profilePhotoTrace(student.getId(), school);
        DocumentDesignTrace documentDesign = documentDesignTrace(period, enrollment, school);
        return new SnapshotTrace(period.getId(), period.getVersion(), enrollment.getId(), classId, curriculum, assessments,
                subjectAssignments, homeroomAssignments, childSnapshotTraces(period, student.getId()),
                FORMULA_VERSION, period.getCalculationPolicy(), profilePhoto, documentDesign,
                List.of(), List.of(), null);
    }

    private List<ChildSnapshotTrace> childSnapshotTraces(AcademicReportingPeriod period, UUID studentId) {
        return jdbc.query("""
                SELECT d.child_period_id,p.code,v.id,v.version,v.state,v.snapshot_hash
                  FROM academic_reporting_period_dependency d
                  JOIN academic_reporting_period p ON p.id=d.child_period_id
                  LEFT JOIN LATERAL (
                      SELECT id,version,state,snapshot_hash
                        FROM bulletin_version
                       WHERE school_id=? AND student_id=? AND reporting_period_id=d.child_period_id
                         AND state IN ('PUBLISHED','VALIDATED')
                       ORDER BY CASE WHEN state='PUBLISHED' THEN 0 ELSE 1 END, published_at DESC NULLS LAST, created_at DESC
                       LIMIT 1
                  ) v ON true
                 WHERE d.school_id=? AND d.academic_session_id=? AND d.parent_period_id=?
                 ORDER BY d.display_order,p.code
                """, (rs, n) -> new ChildSnapshotTrace(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getObject(4) == null ? 0L : rs.getLong(4),
                        rs.getString(5), rs.getString(6)), TenantContext.get(), studentId, TenantContext.get(),
                period.getAcademicSessionId(), period.getId());
    }

    private SnapshotTrace snapshotTrace(AcademicReportingPeriod period, Student student,
                                        StudentEnrollment enrollment, Calculation calculation) {
        SnapshotTrace base = snapshotTrace(period, student, enrollment);
        return new SnapshotTrace(base.reportingPeriodId(), base.reportingPeriodVersion(), base.enrollmentId(), base.classId(),
                base.curriculum(), base.assessments(), base.subjectAssignments(), base.homeroomAssignments(),
                List.of(), FORMULA_VERSION, base.calculationPolicy(), base.profilePhoto(), base.documentDesign(),
                calculation == null ? List.of() : calculation.dependencySources(),
                calculation == null ? List.of() : calculation.packetTraces(),
                calculation == null ? null : calculation.sourceHash());
    }
    private record SnapshotPayload(String periodCode, String periodLabel, String periodType, UUID studentId, String matricule, String studentName, String educationalLevel, String subsystem, String className, List<BulletinLineView> lines, BigDecimal average, Integer rank, int classSize, List<String> blockers, List<BulletinIssueView> issues, String calculationPolicy, AttendanceSummaryView attendance, ConductSummaryView conduct, ClassStatsView classStats, List<GroupStatsView> groupStats, SnapshotTrace trace, AuthoritativeSnapshotView snapshot, String canonicalSnapshotHash) {}
    private record SnapshotTrace(UUID reportingPeriodId, long reportingPeriodVersion, UUID enrollmentId, UUID classId,
                                 List<CurriculumTrace> curriculum, List<AssessmentTrace> assessments,
                                 List<AssignmentTrace> subjectAssignments, List<AssignmentTrace> homeroomAssignments,
                                 List<ChildSnapshotTrace> childSnapshots,
                                 String formulaVersion, String calculationPolicy,
                                 ProfileAssetTrace profilePhoto, DocumentDesignTrace documentDesign,
                                 List<DependencySourceTrace> dependencySources,
                                 List<PacketTrace> packetTraces, String sourceHash) {}
    private record ChildSnapshotTrace(UUID reportingPeriodId, String periodCode, UUID snapshotId,
                                      long snapshotVersion, String state, String snapshotHash) {}
    private record ProfileAssetTrace(UUID assetVersionId, String ownerType, UUID ownerId, String contentType,
                                     long byteSize, java.time.Instant capturedAt, String sha256,
                                     Integer width, Integer height, String fallbackDecision) {}
    private record DocumentDesignTrace(UUID templateId, String templateFamily, String product, String locale,
                                       int templateVersion, String templateHash, UUID brandingId,
                                       int brandingVersion, String brandingHash, String principalName,
                                       String principalTitle, String classMasterTitle, String councilTitle,
                                       String templateConfigJson) {}
    private record TemplateCandidate(UUID id, String templateFamily, String product, String locale,
                                     int templateVersion, String bodyTemplate, String checksum,
                                     String configJson) {}
    private record BrandingCandidate(UUID id, int version, String contentHash, String principalName,
                                     String principalTitle, String classMasterTitle, String councilTitle) {}
    private record DependencyRow(UUID parentPeriodId, UUID childPeriodId, String childCode,
                                 String childLabel, String childPeriodType, long childPeriodVersion,
                                 BigDecimal weight, boolean optional, int displayOrder) {}
    private record CurriculumTrace(UUID id, String subjectCode, int coefficient, long version) {}
    private record AssessmentTrace(UUID id, String code, long version, UUID gradeId, Long gradeVersion,
                                   String gradeStatus, BigDecimal mark) {}
    private record AssignmentTrace(UUID id, long version, String subjectCode, UUID teacherId, String role,
                                   java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo) {}

    private SnapshotEvidenceView evidence(SnapshotTrace trace) {
        if (trace == null) return null;
        ProfileAssetEvidenceView photo = trace.profilePhoto() == null ? null : new ProfileAssetEvidenceView(
                trace.profilePhoto().assetVersionId(), trace.profilePhoto().ownerType(), trace.profilePhoto().ownerId(),
                trace.profilePhoto().contentType(), trace.profilePhoto().byteSize(), trace.profilePhoto().capturedAt(),
                trace.profilePhoto().sha256(), trace.profilePhoto().width(), trace.profilePhoto().height(),
                trace.profilePhoto().fallbackDecision());
        DocumentDesignEvidenceView design = trace.documentDesign() == null ? null : new DocumentDesignEvidenceView(
                trace.documentDesign().templateId(), trace.documentDesign().templateFamily(), trace.documentDesign().product(),
                trace.documentDesign().locale(), trace.documentDesign().templateVersion(), trace.documentDesign().templateHash(),
                trace.documentDesign().brandingId(), trace.documentDesign().brandingVersion(), trace.documentDesign().brandingHash(),
                trace.documentDesign().principalName(), trace.documentDesign().principalTitle(),
                trace.documentDesign().classMasterTitle(), trace.documentDesign().councilTitle(),
                trace.documentDesign().templateConfigJson());
        List<ChildSnapshotEvidenceView> children = trace.childSnapshots() == null ? List.of() : trace.childSnapshots().stream()
                .filter(Objects::nonNull)
                .map(child -> new ChildSnapshotEvidenceView(child.reportingPeriodId(), child.periodCode(), child.snapshotId(),
                        child.snapshotVersion(), child.state(), child.snapshotHash())).toList();
        List<DependencySourceEvidenceView> dependencies = trace.dependencySources() == null ? List.of() : trace.dependencySources().stream()
                .filter(Objects::nonNull)
                .map(source -> new DependencySourceEvidenceView(source.childPeriodId(), source.childPeriodCode(),
                        source.childPeriodVersion(), source.dependencyWeight(), source.optional(), source.sourceKind(),
                        source.sourceHash(), source.packetTraces() == null ? List.of() : source.packetTraces().stream()
                                .map(packet -> new PacketTraceEvidenceView(packet.packetId(), packet.classId(), packet.childReportingPeriodId(),
                                        packet.childReportingPeriodCode(), packet.subjectCode(), packet.status(), packet.version(),
                                        packet.teacherId(), packet.responsibleAssignmentId(), packet.responsibleAssignmentVersion(),
                                        packet.submittedAt(), packet.reviewedAt())).toList())).toList();
        List<PacketTraceEvidenceView> packets = trace.packetTraces() == null ? List.of() : trace.packetTraces().stream()
                .map(packet -> new PacketTraceEvidenceView(packet.packetId(), packet.classId(), packet.childReportingPeriodId(),
                        packet.childReportingPeriodCode(), packet.subjectCode(), packet.status(), packet.version(),
                        packet.teacherId(), packet.responsibleAssignmentId(), packet.responsibleAssignmentVersion(),
                        packet.submittedAt(), packet.reviewedAt())).toList();
        return new SnapshotEvidenceView(photo, design, children, trace.formulaVersion(), trace.calculationPolicy(),
                dependencies, packets, trace.sourceHash());
    }

    private String templateReference(SnapshotTrace trace) {
        if (trace == null || trace.documentDesign() == null) return null;
        DocumentDesignTrace design = trace.documentDesign();
        // bulletin_version.template_version is a compact display/reference
        // field; the immutable snapshot trace carries the full UUIDs and
        // checksums.  Keep this value within the historical VARCHAR(64) bound.
        String template = design.templateId() == null ? "none" :
                (design.templateFamily() == null ? "template" : design.templateFamily()) + ":v" + design.templateVersion();
        String branding = design.brandingId() == null ? "none" : "branding:v" + design.brandingVersion();
        return "template=" + template + ";" + branding;
    }

    private void freezeDesign(BulletinVersion version, SnapshotTrace trace, StudentEnrollment enrollment) {
        if (trace == null || trace.documentDesign() == null) return;
        DocumentDesignTrace design = trace.documentDesign();
        version.setTemplateId(design.templateId());
        version.setTemplateHash(design.templateHash());
        version.setTemplateConfigJson(design.templateConfigJson());
        version.setBrandingId(design.brandingId());
        version.setBrandingVersion(design.brandingVersion());
        version.setBrandingHash(design.brandingHash());
        version.setResolvedAssetHash(resolvedAssetHash(trace));
        version.setSnapshotLocale(design.locale());
        version.setEvidenceGeneratedAt(Instant.now());
    }

    private String resolvedAssetHash(SnapshotTrace trace) {
        if (trace == null) return null;
        String photo = trace.profilePhoto() == null ? "" : String.valueOf(trace.profilePhoto().sha256());
        String branding = trace.documentDesign() == null ? "" : String.valueOf(trace.documentDesign().brandingHash());
        return sha256(photo + "|" + branding);
    }

    private ProfileAssetTrace profilePhotoTrace(UUID studentId, UUID schoolId) {
        List<ProfileAssetTrace> rows = jdbc.query("""
                SELECT id,owner_type,owner_id,content_type,byte_size,captured_at,sha256,width_px,height_px,fallback_decision
                  FROM profile_photo_version
                 WHERE owner_type='student' AND owner_id=? AND school_id=?
                 ORDER BY captured_at DESC
                 LIMIT 1
                """, (rs, n) -> {
            java.sql.Timestamp captured = rs.getTimestamp("captured_at");
            return new ProfileAssetTrace(rs.getObject("id", UUID.class), rs.getString("owner_type"),
                    rs.getObject("owner_id", UUID.class), rs.getString("content_type"), rs.getLong("byte_size"),
                    captured == null ? null : captured.toInstant(), rs.getString("sha256"),
                    (Integer) rs.getObject("width_px"), (Integer) rs.getObject("height_px"), rs.getString("fallback_decision"));
        }, studentId, schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DocumentDesignTrace documentDesignTrace(AcademicReportingPeriod period, StudentEnrollment enrollment, UUID schoolId) {
        String product = switch (period.getPeriodType()) {
            case "SEQUENCE" -> "SEQUENCE";
            case "TERM_RESULT" -> "TERM";
            case "ANNUAL_RESULT" -> "ANNUAL";
            default -> "GENERIC";
        };
        String subsystem = "secondary".equalsIgnoreCase(enrollment.getLevelSnapshot()) ? "SEC" : "PRI";
        String locale = "EN".equalsIgnoreCase(enrollment.getSubsystemSnapshot()) ? "en" : "fr";
        List<TemplateCandidate> candidates = jdbc.query("""
                SELECT id,template_family,product,locale,template_version,body_template,
                       checksum,config_json::text
                  FROM document_template
                 WHERE school_id=? AND active AND status='PUBLISHED' AND locale=?
                   AND product IN (?, 'GENERIC') AND (subsystem=? OR subsystem IS NULL)
                   AND (effective_from IS NULL OR effective_from<=?)
                   AND (effective_to IS NULL OR effective_to>=?)
                  ORDER BY CASE
                             WHEN product=? AND subsystem=? AND reference_family='SECONDARY' THEN 0
                            WHEN product=? AND template_family='REFERENCE' AND subsystem=? THEN 1
                            WHEN product=? AND template_family='GENERIC' THEN 2
                            WHEN product='GENERIC' AND template_family='GENERIC' THEN 3
                            ELSE 4 END,
                          template_version DESC
                 LIMIT 1
                """, (rs, n) -> new TemplateCandidate(rs.getObject("id", UUID.class), rs.getString("template_family"),
                         rs.getString("product"), rs.getString("locale"), rs.getInt("template_version"),
                         rs.getString("body_template"), rs.getString("checksum"), rs.getString("config_json")),
                schoolId, locale, product, subsystem, period.getEndDate(), period.getStartDate(),
                product, subsystem, product, subsystem, product);
        TemplateCandidate template = candidates.isEmpty() ? null : candidates.get(0);
        BrandingCandidate branding = jdbc.query("""
                SELECT id,version,content_hash,principal_name,principal_title,class_master_title,council_title
                  FROM document_branding_version
                 WHERE school_id=? AND status='PUBLISHED' AND locale IN (?, 'fr')
                 ORDER BY CASE WHEN locale=? THEN 0 ELSE 1 END, version DESC
                 LIMIT 1
                """, rs -> rs.next() ? new BrandingCandidate(rs.getObject("id", UUID.class), rs.getInt("version"),
                        rs.getString("content_hash"), rs.getString("principal_name"), rs.getString("principal_title"),
                        rs.getString("class_master_title"), rs.getString("council_title")) : null,
                schoolId, locale, locale);
        if (template == null && branding == null) return null;
        return new DocumentDesignTrace(template == null ? null : template.id(),
                template == null ? null : template.templateFamily(), template == null ? null : template.product(),
                template == null ? locale : template.locale(), template == null ? 0 : template.templateVersion(),
                template == null ? null : sha256(template.bodyTemplate() + "\n" + value(template.configJson())), branding == null ? null : branding.id(),
                branding == null ? 0 : branding.version(), branding == null ? null : branding.contentHash(),
                branding == null ? null : branding.principalName(), branding == null ? null : branding.principalTitle(),
                branding == null ? null : branding.classMasterTitle(), branding == null ? null : branding.councilTitle(),
                template == null ? null : template.configJson());
    }

    private List<GroupStatsView> groupStats(List<BulletinLineView> lines) {
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        for (BulletinLineView line : lines == null ? List.<BulletinLineView>of() : lines) {
            if (line.subjectGroupCode() == null || line.subjectGroupCode().isBlank()) continue;
            GroupAccumulator group = groups.computeIfAbsent(line.subjectGroupCode(), key ->
                    new GroupAccumulator(line.subjectGroupCode(), line.subjectGroupLabel()));
            group.total = group.total.add(line.weighted() == null ? BigDecimal.ZERO : line.weighted());
            group.coefficient += Math.max(0, line.coefficient());
            group.subjectCount++;
        }
        return groups.values().stream().map(group -> new GroupStatsView(group.code, group.label,
                group.coefficient == 0 ? BigDecimal.ZERO : group.total.divide(BigDecimal.valueOf(group.coefficient), 4, RoundingMode.HALF_UP),
                group.total, group.coefficient, group.subjectCount)).toList();
    }
    private static final class GroupAccumulator {
        private final String code;
        private final String label;
        private BigDecimal total = BigDecimal.ZERO;
        private int coefficient;
        private int subjectCount;
        private GroupAccumulator(String code, String label) { this.code = code; this.label = label; }
    }
    private AcademicReportingPeriod period(UUID id){return periods.findByIdAndSchoolId(id,TenantContext.get()).orElseThrow(()->ApiException.notFound("Période de résultat"));}
    private String subjectLabel(Subject s,String code){if(s==null)return code; Map<String,String> l=s.getLabel(); return l==null?code:(l.getOrDefault("fr",l.getOrDefault("en",code)));}
    /**
     * Resolve the effective coefficient for the student's enrolled class.
     * Subject.coef is only a creation default.  Once a published session
     * curriculum row exists, it is the immutable coefficient authority.
     */
    private Map<String, Integer> effectiveCoefficients(UUID studentId, UUID sessionId) {
        UUID schoolId = TenantContext.get();
        List<Subject> catalog = subjects.findBySchoolIdOrderByCode(schoolId);
        Map<String, Integer> byCode = new HashMap<>();
        for (Subject subject : catalog) {
            byCode.put(subject.getCode(), subject.getCoef());
        }

        // The session curriculum is authoritative; the legacy class override
        // remains only as a compatibility fallback for sessions not migrated yet.
        jdbc.query("SELECT s.code, c.coefficient FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?",
                rs -> { while (rs.next()) byCode.put(rs.getString(1), rs.getInt(2)); return null; },
                schoolId, sessionId, enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(schoolId, studentId, sessionId, "ACTIVE").map(StudentEnrollment::getSchoolClassId).orElse(null));

        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                schoolId, studentId, sessionId, "ACTIVE").orElse(null);
        if (enrollment == null) return byCode;

        return byCode;
    }
    private CurriculumMetadata curriculumMetadata(UUID studentId, UUID sessionId, String subjectCode) {
        UUID classId = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, sessionId, "ACTIVE").map(StudentEnrollment::getSchoolClassId).orElse(null);
        if (classId == null) return new CurriculumMetadata(null, null, false, null);
        CurriculumMetadata base = jdbc.query("SELECT g.code, COALESCE(g.label->>'fr',g.label->>'en',g.code), c.remark_required "
                        + "FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id "
                        + "LEFT JOIN academic_subject_group g ON g.id=c.group_id "
                        + "WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? AND s.code=?",
                rs -> rs.next() ? new CurriculumMetadata(rs.getString(1), rs.getString(2), rs.getBoolean(3), null) : new CurriculumMetadata(null, null, false, null),
                TenantContext.get(), sessionId, classId, subjectCode);
        LocalDate effectiveDate = jdbc.queryForObject("SELECT start_date FROM academic_session WHERE id=? AND school_id=?",
                LocalDate.class, sessionId, TenantContext.get());
        TeachingAssignmentResolver.Resolution resolved = assignments.resolve(sessionId, classId, subjectCode, effectiveDate);
        return new CurriculumMetadata(base.groupCode(), base.groupLabel(), base.remarkRequired(),
                resolved.available() ? resolved.teacherName() : null);
    }
    private record CurriculumMetadata(String groupCode, String groupLabel, boolean remarkRequired, String teacherName) {}
    private String appreciation(BigDecimal a){return a == null ? "EXEMPT" : a.compareTo(BigDecimal.valueOf(16))>=0?"Excellent":a.compareTo(BigDecimal.valueOf(14))>=0?"Très bien":a.compareTo(BigDecimal.valueOf(12))>=0?"Bien":a.compareTo(BigDecimal.valueOf(10))>=0?"Acquis":"En cours d'acquisition";}
    private AttendanceSummaryView attendance(AcademicReportingPeriod p, UUID studentId) {
        return attendanceEvidence.aggregate(p.getId(), studentId);
    }
    private ConductSummaryView conduct(AcademicReportingPeriod p, UUID studentId) {
        return attendanceEvidence.conductSummary(p.getId(), studentId);
    }
    private String sha256(String v){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha256(byte[] value){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static String value(String value){return value == null ? "" : value;}
    private UUID currentUserId(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p?p.userId():null;}

    @Transactional(readOnly = true)
    public FormulaDrilldownView formula(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        teacherScope.assertStudent(version.getStudentId());
        SnapshotPayload payload = readPayload(version);
        AuthoritativeSnapshotView snapshot = payload.snapshot();
        if (snapshot == null) throw ApiException.conflict("Ce bulletin ne contient pas le contrat de snapshot BAY-35");
        List<FormulaStepView> steps = new ArrayList<>();
        for (SnapshotSubjectResultView subject : snapshot.result() == null ? List.<SnapshotSubjectResultView>of() : snapshot.result().subjects()) {
            for (AssessmentEvidenceView assessment : subject.assessments()) {
                BigDecimal precise = assessment.mark();
                BigDecimal weight = assessment.weight() == null ? BigDecimal.ONE : assessment.weight();
                BigDecimal contribution = precise == null ? null : precise.multiply(weight);
                steps.add(new FormulaStepView(subject.subjectCode(), assessment.code(), precise, display(precise),
                        weight, contribution, display(contribution), assessment.status(), sourceLabel(snapshot, assessment.code())));
            }
            for (PeriodMarkView component : subject.components()) {
                BigDecimal precise = component.mark();
                steps.add(new FormulaStepView(subject.subjectCode(), component.periodCode(), precise, display(precise),
                        BigDecimal.ONE, precise, display(precise), precise == null ? "MISSING" : "SCORED", component.periodCode()));
            }
        }
        return new FormulaDrilldownView(version.getId(), version.getVersion(), version.getSnapshotHash(),
                snapshot.formulaVersion(), snapshot.calculationPolicy(), snapshot.product(), steps, snapshot.sourceVersions());
    }

    @Transactional(readOnly = true)
    public SnapshotDiffView diff(UUID fromId, UUID toId) {
        BulletinVersion from = versions.findByIdAndSchoolId(fromId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version source"));
        BulletinVersion to = versions.findByIdAndSchoolId(toId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version cible"));
        teacherScope.assertStudent(from.getStudentId());
        teacherScope.assertStudent(to.getStudentId());
        if (!Objects.equals(from.getStudentId(), to.getStudentId()))
            throw ApiException.badRequest("Les versions comparées doivent concerner le même élève");
        try {
            com.fasterxml.jackson.databind.JsonNode left = mapper.readTree(from.getSnapshotJson());
            com.fasterxml.jackson.databind.JsonNode right = mapper.readTree(to.getSnapshotJson());
            List<SnapshotDiffEntryView> changes = new ArrayList<>();
            String[] paths = {"/snapshot/student", "/snapshot/enrollment", "/snapshot/permittedGuardian",
                    "/snapshot/staff", "/snapshot/profilePhoto", "/snapshot/school", "/snapshot/curriculum",
                    "/snapshot/result", "/snapshot/evidence", "/snapshot/attendance", "/snapshot/conduct",
                    "/snapshot/formulaVersion", "/snapshot/calculationPolicy", "/snapshot/sourceVersions"};
            for (String path : paths) {
                com.fasterxml.jackson.databind.JsonNode a = left.at(path);
                com.fasterxml.jackson.databind.JsonNode b = right.at(path);
                if (!Objects.equals(a, b)) changes.add(new SnapshotDiffEntryView(path, nodeValue(a), nodeValue(b)));
            }
            return new SnapshotDiffView(fromId, toId, from.getSnapshotHash(), to.getSnapshotHash(), changes.isEmpty(), changes);
        } catch (JsonProcessingException ex) {
            throw ApiException.conflict("Impossible de comparer les snapshots");
        }
    }

    private SnapshotPayload readPayload(BulletinVersion version) {
        try { return mapper.readValue(version.getSnapshotJson(), SnapshotPayload.class); }
        catch (JsonProcessingException ex) { throw ApiException.conflict("Snapshot de bulletin illisible"); }
    }

    private String sourceLabel(AuthoritativeSnapshotView snapshot, String code) {
        return snapshot.sourceVersions().stream().filter(x -> Objects.equals(x.label(), code))
                .map(x -> x.sourceId() + "@" + x.sourceVersion()).findFirst().orElse(code);
    }

    private String nodeValue(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null || node.isMissingNode() ? null : node.toString();
    }
}
