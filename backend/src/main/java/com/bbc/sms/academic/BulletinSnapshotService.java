package com.bbc.sms.academic;

import com.bbc.sms.academic.calculation.AcademicCalculationEngine;
import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.timetable.TeachingAssignmentResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class BulletinSnapshotService {
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
    private final AcademicAccessPolicyService accessPolicy;
    private final TeachingAssignmentResolver assignments;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final AuthorizationPolicyService policy;

    public BulletinSnapshotService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                   AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                   AcademicGradePacketRepository packets, BulletinVersionRepository versions, StudentEnrollmentRepository enrollments,
                                   StudentRepository students, SubjectRepository subjects,
                                   SubjectClassCoefRepository subjectClassCoefs, SchoolClassRepository classes,
                                   AcademicWindowPolicyService windows, AcademicAccessPolicyService accessPolicy, TeachingAssignmentResolver assignments, ObjectMapper mapper,
                                   JdbcTemplate jdbc, AuditService audit, AuthorizationPolicyService policy) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments; this.packets = packets;
        this.versions = versions; this.enrollments = enrollments; this.students = students; this.subjects = subjects;
        this.subjectClassCoefs = subjectClassCoefs; this.classes = classes;
        this.windows = windows; this.accessPolicy = accessPolicy; this.assignments = assignments; this.mapper = mapper; this.jdbc = jdbc; this.audit = audit; this.policy = policy;
    }

    private StudentEnrollment enrollment(UUID studentId, AcademicReportingPeriod period) {
        return enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE")
                .filter(e -> !e.getEnrolledOn().isAfter(period.getStartDate())
                        && (e.getExitedOn() == null || !e.getExitedOn().isBefore(period.getStartDate())))
                .orElse(null);
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
        String json = writeSnapshot(period, student, enrollment, calculation, attendance, conduct, trace);
        return new CurrentSnapshot(student, enrollment, calculation, attendance, conduct, trace, json, sha256(json));
    }

    private List<String> officialBlockers(Calculation calculation, ConductSummaryView conduct) {
        List<String> blockers = new ArrayList<>(calculation == null ? List.of() : calculation.blockers());
        if (conduct == null || !"APPROVED".equalsIgnoreCase(conduct.status())) addDistinct(blockers, "CONDUCT_NOT_APPROVED");
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
        AcademicReportingPeriod period = period(periodId);
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.REPORT_CARD_VALIDATE,
                studentId, period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollment(studentId, period);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée. Vérifiez son inscription dans Élèves > Inscription.");
        BulletinVersion official = latestOfficial(studentId, periodId);
        BulletinVersion active = latestActive(studentId, periodId);
        if (official != null && active == null) return viewFromSnapshot(official, period, student);
        CurrentSnapshot current = currentSnapshot(studentId, period, student, enrollment);
        List<String> officialBlockers = officialBlockers(current.calculation(), current.conduct());
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
        version.setTemplateVersion(templateReference(current.trace()));
        freezeDesign(version, current.trace(), enrollment);
        BulletinVersion saved = versions.save(version);
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
        windows.assertOpen(previous.getReportingPeriodId(), AcademicWindowPolicyService.Action.VALIDATION);
        AcademicReportingPeriod period = period(previous.getReportingPeriodId());
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.REPORT_CARD_VALIDATE,
                previous.getStudentId(), period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(previous.getStudentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollment(previous.getStudentId(), period);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour l'actualisation.");
        CurrentSnapshot current = currentSnapshot(previous.getStudentId(), period, student, enrollment);
        List<String> blockers = officialBlockers(current.calculation(), current.conduct());
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
        replacement.setSupersedesId(previous.getId());
        replacement.setGeneralAppreciation(previous.getGeneralAppreciation());
        replacement.setTemplateVersion(templateReference(current.trace()));
        freezeDesign(replacement, current.trace(), enrollment);
        BulletinVersion saved = versions.saveAndFlush(replacement);
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
        BulletinVersion previous = versions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!Set.of("VALIDATED", "PUBLISHED").contains(previous.getState()))
            throw ApiException.conflict("Une correction ne peut commencer que depuis un bulletin validé ou publié");
        if (request == null || request.reason() == null || request.reason().isBlank())
            throw ApiException.badRequest("Le motif de correction est obligatoire");
        if (request.version() != null && request.version() != previous.getVersion())
            throw ApiException.conflict("Le bulletin a été modifié entre-temps. Rechargez-le avant de corriger.");
        windows.assertOpen(previous.getReportingPeriodId(), AcademicWindowPolicyService.Action.CORRECTION);
        AcademicReportingPeriod period = period(previous.getReportingPeriodId());
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.REPORT_CARD_VALIDATE,
                previous.getStudentId(), period.getAcademicSessionId(), period.getStartDate(), null);
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
        replacement.setCorrectsBulletinVersionId(previous.getId());
        replacement.setSupersedesId(previous.getId());
        replacement.setCorrectionReason(request.reason().trim());
        replacement.setCorrectionRequestedBy(currentUserId());
        replacement.setCorrectionRequestedAt(Instant.now());
        replacement.setTemplateVersion(templateReference(trace));
        freezeDesign(replacement, trace, enrollment);
        return view(versions.save(replacement), period, student, calculation, attendance, conduct, trace);
    }

    @Transactional(readOnly = true)
    public BulletinSnapshotView latest(UUID studentId, UUID periodId) {
        AcademicReportingPeriod period = period(periodId);
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW,
                studentId, period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        BulletinVersion version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(TenantContext.get(), studentId, periodId)
                .orElseThrow(() -> ApiException.notFound("Aucun calcul de bulletin"));
        return viewFromSnapshot(version, period, student);
    }

    /** Pure calculation used by read-only class PV and preview screens. */
    @Transactional(readOnly = true)
    public BulletinSnapshotView preview(UUID studentId, UUID periodId) {
        AcademicReportingPeriod period = period(periodId);
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW,
                studentId, period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollment(studentId, period);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée.");
        // A preview never creates a version. If an explicit draft/correction already
        // exists, show that durable version so the user can continue its workflow;
        // otherwise expose the latest frozen result before calculating an in-memory
        // preview from the current authoritative inputs.
        CurrentSnapshot current = currentSnapshot(studentId, period, student, enrollment);
        BulletinVersion active = latestActive(studentId, periodId);
        if (active != null) {
            if (Objects.equals(active.getSnapshotHash(), current.hash()))
                return persistedView(active, period, student, current, "CURRENT", false);
            return currentView(current, period, student, active, "STALE", true);
        }
        BulletinVersion official = latestOfficial(studentId, periodId);
        if (official != null) return viewFromSnapshot(official, period, student);
        return currentView(current, period, student, null, "NONE", false);
    }

    @Transactional(readOnly = true)
    public BulletinSnapshotView byId(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW,
                version.getStudentId(), period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        return viewFromSnapshot(version, period, student);
    }

    /**
     * Official report-card generation is a STUDENT-scoped V2 action. Resolve
     * its resource context from the persisted snapshot/enrollment rather than
     * relying on a legacy role-only controller check or a client header.
     */
    @Transactional(readOnly = true)
    public void requireDocumentGeneration(BulletinSnapshotView snapshot) {
        AcademicReportingPeriod period = period(snapshot.reportingPeriodId());
        StudentEnrollment active = enrollment(snapshot.studentId(), period);
        if (active == null || active.getSchoolClassId() == null) {
            throw ApiException.forbidden("L'inscription active de l'élève est requise pour générer le document.");
        }
        String level = Optional.ofNullable(active.getLevelSnapshot()).filter(value -> !value.isBlank())
                .orElse(snapshot.educationalLevel());
        String subsystem = Optional.ofNullable(active.getSubsystemSnapshot()).filter(value -> !value.isBlank())
                .orElse(snapshot.subsystem());
        policy.require("DOCUMENT_GENERATE", new PolicyResourceContext(
                TenantContext.get(), snapshot.academicSessionId(), period.getStartDate(),
                new ParcoursContext.Scope(level.toLowerCase(Locale.ROOT), subsystem.toUpperCase(Locale.ROOT)),
                active.getSchoolClassId(), null, snapshot.studentId(), null, snapshot.id(), null, null, level));
    }

    @Transactional
    public BulletinSnapshotView validate(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolIdForUpdate(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!"DRAFT".equals(version.getState()) && !"RETURNED".equals(version.getState())) throw ApiException.conflict("Cette version n'est plus un brouillon validable");
        windows.assertOpen(version.getReportingPeriodId(), AcademicWindowPolicyService.Action.VALIDATION);
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.REPORT_CARD_VALIDATE,
                version.getStudentId(), period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get()).orElseThrow();
        StudentEnrollment enrollment = enrollment(version.getStudentId(), period);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour la validation.");
        CurrentSnapshot current = currentSnapshot(version.getStudentId(), period, student, enrollment);
        if (!Objects.equals(version.getSnapshotHash(), current.hash())) {
            throw ApiException.blockers("BULLETIN_DRAFT_STALE",
                    "Le brouillon ne correspond plus aux sources actuelles. Actualisez-le avant validation.",
                    List.of("BULLETIN_DRAFT_STALE"));
        }
        List<String> blockers = officialBlockers(current.calculation(), current.conduct());
        if (!blockers.isEmpty()) throw ApiException.blockers("BULLETIN_NOT_READY",
                "Bulletin incomplet ou preuves administratives non approuvées : " + String.join("; ", blockers), blockers);
        version.setState("VALIDATED"); version.setValidatedAt(Instant.now()); version.setValidatedBy(currentUserId());
        versions.saveAndFlush(version);
        audit.record("BULLETIN_VALIDATED", "BulletinVersion", version.getId().toString(),
                Map.of("state", "DRAFT", "snapshotHash", version.getSnapshotHash()),
                Map.of("state", "VALIDATED", "snapshotHash", version.getSnapshotHash()), null);
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
        accessPolicy.requireStudent(AcademicAccessPolicyService.Capability.REPORT_CARD_PUBLISH,
                version.getStudentId(), period.getAcademicSessionId(), period.getStartDate(), null);
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get()).orElseThrow();
        StudentEnrollment enrollment = enrollment(version.getStudentId(), period);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour la publication.");
        CurrentSnapshot current = currentSnapshot(version.getStudentId(), period, student, enrollment);
        if (!Objects.equals(version.getSnapshotHash(), current.hash())) {
            throw ApiException.blockers("BULLETIN_DRAFT_STALE",
                    "Le bulletin validé ne correspond plus aux sources actuelles.", List.of("BULLETIN_DRAFT_STALE"));
        }
        List<String> blockers = officialBlockers(current.calculation(), current.conduct());
        if (!blockers.isEmpty()) throw ApiException.blockers("BULLETIN_NOT_READY",
                "Le bulletin ne peut pas être publié : " + String.join("; ", blockers), blockers);
        version.setState("PUBLISHED");
        version.setPublishedAt(Instant.now());
        version.setPublishedBy(currentUserId());
        version.setPublicationReason(request.reason().trim());
        versions.saveAndFlush(version);
        if (version.getCorrectsBulletinVersionId() != null) {
            versions.findByIdAndSchoolId(version.getCorrectsBulletinVersionId(), TenantContext.get()).ifPresent(previous -> {
                previous.setState("SUPERSEDED");
                versions.save(previous);
            });
        }
        audit.record("BULLETIN_PUBLISHED", "BulletinVersion", version.getId().toString(),
                Map.of("state", "VALIDATED", "snapshotHash", version.getSnapshotHash()),
                Map.of("state", "PUBLISHED", "snapshotHash", version.getSnapshotHash(), "reason", request.reason().trim()),
                request.reason().trim());
        return view(version, period, student, current.calculation(), current.attendance(), current.conduct(), current.trace(), "CURRENT", false);
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
        AcademicReportingPeriod period = period(periodId);
        accessPolicy.require(AcademicAccessPolicyService.Capability.CLASS_RESULTS_VIEW,
                period.getAcademicSessionId(), classId, null, null, period.getStartDate());
        SchoolClass schoolClass = classes.findByIdAndSchoolId(classId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Classe"));
        List<StudentEnrollment> roster = enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                TenantContext.get(), period.getAcademicSessionId(), classId, "ACTIVE");
        roster = roster.stream().filter(e -> !e.getEnrolledOn().isAfter(period.getStartDate())
                && (e.getExitedOn() == null || !e.getExitedOn().isBefore(period.getStartDate()))).toList();
        Map<UUID, String> names = jdbc.query("""
                SELECT e.student_id, s.last_name || ' ' || s.first_name
                  FROM student_enrollment e JOIN student s ON s.id=e.student_id
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=? AND e.status='ACTIVE'
                """, rs -> {
            Map<UUID, String> result = new HashMap<>();
            while (rs.next()) result.put(rs.getObject(1, UUID.class), rs.getString(2));
            return result;
        }, TenantContext.get(), period.getAcademicSessionId(), classId);
        Map<UUID, Calculation> calculated = new LinkedHashMap<>();
        CalculationContext context = new CalculationContext();
        for (StudentEnrollment enrollment : roster) {
            calculated.put(enrollment.getStudentId(), calculateCurrent(enrollment.getStudentId(), period, context));
        }
        List<BigDecimal> cohortAverages = new ArrayList<>();
        for (StudentEnrollment enrollment : roster) {
            Calculation current = calculated.get(enrollment.getStudentId());
            BigDecimal average = current.average();
            List<String> blockers = current.blockers();
            if (blockers.isEmpty() && average != null) cohortAverages.add(average);
        }
        List<SessionPvRow> rows = new ArrayList<>();
        for (StudentEnrollment enrollment : roster) {
            UUID studentId = enrollment.getStudentId();
            Calculation calculation = calculated.get(studentId);
            BigDecimal average = calculation.average();
            List<String> blockers = calculation.blockers();
            Integer rank = blockers.isEmpty() && average != null
                    ? 1 + (int) cohortAverages.stream().filter(value -> value.compareTo(average) > 0).count() : null;
            rows.add(new SessionPvRow(null, studentId, names.getOrDefault(studentId, studentId.toString()),
                    average, rank, "PREVIEW", blockers.isEmpty(), blockers));
        }
        rows.sort(Comparator
                .comparing(SessionPvRow::complete).reversed()
                .thenComparing(SessionPvRow::average, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SessionPvRow::studentName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        List<BigDecimal> completeAverages = rows.stream().filter(SessionPvRow::complete).map(SessionPvRow::average).toList();
        BigDecimal classAverage = completeAverages.isEmpty() ? null : completeAverages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(completeAverages.size()), 2, RoundingMode.HALF_UP);
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
            Calculation child = calculateCurrent(studentId, childPeriod, context);
            children.put(dependency.childCode().toUpperCase(Locale.ROOT), child);
            if (!dependency.optional()) {
                for (String childBlocker : child.blockers()) addDistinct(blockers, dependency.childCode() + ":" + childBlocker);
                issues.addAll(child.issues());
            }
            packetTraces.addAll(child.packetTraces());
            readinessRows.add(readinessFor(dependency, child, childPeriod));
            sourceTraces.add(new DependencySourceTrace(childPeriod.getId(), childPeriod.getCode(), childPeriod.getVersion(),
                    dependency.weight(), dependency.optional(), sourceKind(childPeriod), child.sourceHash(), child.packetTraces()));
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
            SubjectResultComment comment = comments.findBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCode(
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

    private String writeSnapshot(AcademicReportingPeriod p, Student s, StudentEnrollment e, Calculation c,
                                 AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace) {
        try {
            return mapper.writeValueAsString(new SnapshotPayload(p.getCode(), p.getLabel(), p.getPeriodType(),
                    s.getId(), s.getMatricule(), s.getLastName() + " " + s.getFirstName(),
                    c.educationalLevel(), c.subsystem(), e.getClassNameSnapshot(), c.lines(), c.average(), c.rank(), c.classSize(), c.blockers(),
                    c.issues(), p.getCalculationPolicy(), attendance, conduct, c.classStats(), groupStats(c.lines()), trace));
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
        List<String> validationBlockers = officialBlockers(c, conduct);
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
        return new BulletinSnapshotView(v.getId(), p.getAcademicSessionId(), p.getId(), p.getCode(), p.getLabel(),
                s.getId(), s.getLastName() + " " + s.getFirstName(), s.getMatricule(), c.educationalLevel(), c.subsystem(), c.className(), c.lines(),
                c.average(), c.rank(), c.classSize(), v.getState(), c.blockers().isEmpty(), c.blockers(),
                v.getSnapshotHash(), v.getCalculationPolicy(), v.getGeneralAppreciation(), attendance, conduct,
                v.getVersion(), c.classStats(), v.getSupersedesId(), v.getCorrectsBulletinVersionId(),
                v.getCorrectionReason(), v.getCorrectionRequestedBy(), v.getCorrectionRequestedAt(),
                groupStats(c.lines()), evidence(trace), p.getPeriodType(), productName(p), workflow, issues);
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
            return view(v, p, s, calculation, x.attendance(), x.conduct(), x.trace(), relation, false);
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
                subjectAssignments, homeroomAssignments, List.of(),
                FORMULA_VERSION, period.getCalculationPolicy(), profilePhoto, documentDesign,
                List.of(), List.of(), null);
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
    private record SnapshotPayload(String periodCode, String periodLabel, String periodType, UUID studentId, String matricule, String studentName, String educationalLevel, String subsystem, String className, List<BulletinLineView> lines, BigDecimal average, Integer rank, int classSize, List<String> blockers, List<BulletinIssueView> issues, String calculationPolicy, AttendanceSummaryView attendance, ConductSummaryView conduct, ClassStatsView classStats, List<GroupStatsView> groupStats, SnapshotTrace trace) {}
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
                                     long byteSize, java.time.Instant capturedAt, String sha256) {}
    private record DocumentDesignTrace(UUID templateId, String templateFamily, String product, String locale,
                                       int templateVersion, String templateHash, UUID brandingId,
                                       int brandingVersion, String brandingHash, String principalName,
                                       String principalTitle, String classMasterTitle, String councilTitle) {}
    private record TemplateCandidate(UUID id, String templateFamily, String product, String locale,
                                     int templateVersion, String bodyTemplate) {}
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
                trace.profilePhoto().sha256());
        DocumentDesignEvidenceView design = trace.documentDesign() == null ? null : new DocumentDesignEvidenceView(
                trace.documentDesign().templateId(), trace.documentDesign().templateFamily(), trace.documentDesign().product(),
                trace.documentDesign().locale(), trace.documentDesign().templateVersion(), trace.documentDesign().templateHash(),
                trace.documentDesign().brandingId(), trace.documentDesign().brandingVersion(), trace.documentDesign().brandingHash(),
                trace.documentDesign().principalName(), trace.documentDesign().principalTitle(),
                trace.documentDesign().classMasterTitle(), trace.documentDesign().councilTitle());
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
        version.setBrandingId(design.brandingId());
        version.setSnapshotLocale(design.locale());
        version.setEvidenceGeneratedAt(Instant.now());
    }

    private ProfileAssetTrace profilePhotoTrace(UUID studentId, UUID schoolId) {
        List<ProfileAssetTrace> rows = jdbc.query("""
                SELECT id,owner_type,owner_id,content_type,byte_size,captured_at,sha256
                  FROM profile_photo_version
                 WHERE owner_type='student' AND owner_id=? AND school_id=?
                 ORDER BY captured_at DESC
                 LIMIT 1
                """, (rs, n) -> {
            java.sql.Timestamp captured = rs.getTimestamp("captured_at");
            return new ProfileAssetTrace(rs.getObject("id", UUID.class), rs.getString("owner_type"),
                    rs.getObject("owner_id", UUID.class), rs.getString("content_type"), rs.getLong("byte_size"),
                    captured == null ? null : captured.toInstant(), rs.getString("sha256"));
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
                SELECT id,template_family,product,locale,template_version,body_template
                  FROM document_template
                 WHERE school_id=? AND active AND status='PUBLISHED' AND locale=?
                   AND product IN (?, 'GENERIC') AND (subsystem=? OR subsystem IS NULL)
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
                        rs.getString("body_template")), schoolId, locale, product, subsystem, product, subsystem, product, subsystem, product);
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
                template == null ? null : sha256(template.bodyTemplate()), branding == null ? null : branding.id(),
                branding == null ? 0 : branding.version(), branding == null ? null : branding.contentHash(),
                branding == null ? null : branding.principalName(), branding == null ? null : branding.principalTitle(),
                branding == null ? null : branding.classMasterTitle(), branding == null ? null : branding.councilTitle());
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
        Map<String, Object> row = jdbc.queryForMap("SELECT count(DISTINCT s.id) AS finalized_sessions, count(*) FILTER (WHERE m.status='PRESENT') AS present_count, count(*) FILTER (WHERE m.status='ABSENT') AS absent_count, count(*) FILTER (WHERE m.status='EXCUSED') AS excused_count, count(*) FILTER (WHERE m.status='LATE') AS late_count, coalesce(sum(m.late_minutes),0) AS late_minutes, coalesce(sum(CASE WHEN m.status='EXCUSED' THEN s.duration_minutes ELSE 0 END),0) / 60.0 AS justified_absence_hours, coalesce(sum(CASE WHEN m.status='ABSENT' THEN s.duration_minutes ELSE 0 END),0) / 60.0 AS unjustified_absence_hours FROM attendance_session s JOIN attendance_mark m ON m.attendance_session_id=s.id WHERE s.school_id=? AND s.academic_session_id=? AND m.student_id=? AND s.status='FINALIZED' AND s.session_date BETWEEN ? AND ?", TenantContext.get(), p.getAcademicSessionId(), studentId, p.getStartDate(), p.getEndDate());
        int finalized = ((Number) row.getOrDefault("finalized_sessions", 0)).intValue(); int present = ((Number) row.getOrDefault("present_count", 0)).intValue(); int absent = ((Number) row.getOrDefault("absent_count", 0)).intValue(); int excused = ((Number) row.getOrDefault("excused_count", 0)).intValue(); int late = ((Number) row.getOrDefault("late_count", 0)).intValue(); int lateMinutes = ((Number) row.getOrDefault("late_minutes", 0)).intValue();
        Map<String, Object> adjustment = jdbc.queryForMap("SELECT coalesce(sum(justified_absence_hours),0) AS justified, coalesce(sum(unjustified_absence_hours),0) AS unjustified, coalesce(sum(late_minutes),0) AS late_minutes FROM attendance_period_adjustment WHERE school_id=? AND reporting_period_id=? AND student_id=? AND status='APPROVED'", TenantContext.get(), p.getId(), studentId);
        return new AttendanceSummaryView(finalized, present, absent, excused, late, lateMinutes, new BigDecimal(row.get("justified_absence_hours").toString()), new BigDecimal(row.get("unjustified_absence_hours").toString()), new BigDecimal(adjustment.get("justified").toString()), new BigDecimal(adjustment.get("unjustified").toString()), ((Number) adjustment.get("late_minutes")).intValue());
    }
    private ConductSummaryView conduct(AcademicReportingPeriod p, UUID studentId) { return jdbc.query("SELECT work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,encouragement,congratulations,exclusion_days,decision_code,council_observation,status FROM student_period_conduct WHERE school_id=? AND reporting_period_id=? AND student_id=?", rs -> rs.next() ? new ConductSummaryView(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11)) : new ConductSummaryView(false,false,false,false,false,false,false,0,null,null,"DRAFT"), TenantContext.get(), p.getId(), studentId); }
    private String sha256(String v){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private String sha256(byte[] value){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private UUID currentUserId(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p?p.userId():null;}
}
