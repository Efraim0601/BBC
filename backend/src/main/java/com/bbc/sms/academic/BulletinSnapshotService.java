package com.bbc.sms.academic;

import com.bbc.sms.academic.calculation.AcademicCalculationEngine;
import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
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
    private final AcademicReportingPeriodRepository periods;
    private final AcademicAssessmentRepository assessments;
    private final AcademicGradeRepository grades;
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

    public BulletinSnapshotService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                   AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                   BulletinVersionRepository versions, StudentEnrollmentRepository enrollments,
                                   StudentRepository students, SubjectRepository subjects,
                                   SubjectClassCoefRepository subjectClassCoefs, SchoolClassRepository classes,
                                   AcademicWindowPolicyService windows, TeacherScopeService teacherScope, TeachingAssignmentResolver assignments, ObjectMapper mapper,
                                   JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments;
        this.versions = versions; this.enrollments = enrollments; this.students = students; this.subjects = subjects;
        this.subjectClassCoefs = subjectClassCoefs; this.classes = classes;
        this.windows = windows; this.teacherScope = teacherScope; this.assignments = assignments; this.mapper = mapper; this.jdbc = jdbc;
    }

    @Transactional
    public BulletinSnapshotView calculate(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(periodId);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée. Vérifiez son inscription dans Élèves > Inscription.");
        BulletinVersion published = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                TenantContext.get(), studentId, periodId, "PUBLISHED").orElse(null);
        if (published != null) return viewFromSnapshot(published, period, student);
        BulletinVersion existing = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(
                TenantContext.get(), studentId, periodId).orElse(null);
        if (existing != null && "VALIDATED".equals(existing.getState())) {
            return viewFromSnapshot(existing, period, student);
        }
        Calculation calculation = withClassStatistics(studentId, period, calculatePeriod(studentId, period));
        AttendanceSummaryView attendance = attendance(period, studentId);
        ConductSummaryView conduct = conduct(period, studentId);
        SnapshotTrace trace = snapshotTrace(period, student, enrollment);
        String json = writeSnapshot(period, student, enrollment, calculation, attendance, conduct, trace);
        BulletinVersion version = new BulletinVersion();
        version.setSchoolId(TenantContext.get()); version.setAcademicSessionId(period.getAcademicSessionId()); version.setReportingPeriodId(periodId);
        version.setStudentId(studentId); version.setEnrollmentId(enrollment.getId()); version.setState("DRAFT"); version.setSnapshotJson(json);
        version.setSnapshotHash(sha256(json)); version.setAverage(calculation.average()); version.setRank(calculation.rank()); version.setClassSize(calculation.classSize());
        version.setCalculationPolicy(period.getCalculationPolicy()); version.setCreatedBy(currentUserId());
        version.setTemplateVersion(templateReference(trace));
        return view(versions.save(version), period, student, calculation, attendance, conduct, trace);
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
        Student student = students.findByIdAndSchoolId(previous.getStudentId(), TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), previous.getStudentId(), period.getAcademicSessionId(), "ACTIVE")
                .orElseThrow(() -> ApiException.conflict("Aucune inscription active pour la correction"));
        Calculation calculation = withClassStatistics(previous.getStudentId(), period, calculatePeriod(previous.getStudentId(), period));
        AttendanceSummaryView attendance = attendance(period, previous.getStudentId());
        ConductSummaryView conduct = conduct(period, previous.getStudentId());
        SnapshotTrace trace = snapshotTrace(period, student, enrollment);
        String json = writeSnapshot(period, student, enrollment, calculation, attendance, conduct, trace);
        BulletinVersion replacement = new BulletinVersion();
        replacement.setSchoolId(TenantContext.get());
        replacement.setAcademicSessionId(period.getAcademicSessionId());
        replacement.setReportingPeriodId(period.getId());
        replacement.setStudentId(previous.getStudentId());
        replacement.setEnrollmentId(enrollment.getId());
        replacement.setState("DRAFT");
        replacement.setSnapshotJson(json);
        replacement.setSnapshotHash(sha256(json));
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
        return view(versions.save(replacement), period, student, calculation, attendance, conduct, trace);
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
        // A preview never creates a version. If an explicit draft/correction already
        // exists, show that durable version so the user can continue its workflow;
        // otherwise expose the latest frozen result before calculating an in-memory
        // preview from the current authoritative inputs.
        BulletinVersion latest = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(
                TenantContext.get(), studentId, periodId).orElse(null);
        if (latest != null && Set.of("DRAFT", "RETURNED", "CALCULATED", "READY", "VALIDATED", "PUBLISHED").contains(latest.getState())) {
            return viewFromSnapshot(latest, period, student);
        }
        BulletinVersion published = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                TenantContext.get(), studentId, periodId, "PUBLISHED").orElse(null);
        if (published != null) return viewFromSnapshot(published, period, student);
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        if (enrollment == null) throw ApiException.conflict("Cet élève n'est pas inscrit dans la session académique sélectionnée. Vérifiez son inscription dans Élèves > Inscription.");
        Calculation calculation = withClassStatistics(studentId, period, calculatePeriod(studentId, period));
        AttendanceSummaryView attendance = attendance(period, studentId);
        ConductSummaryView conduct = conduct(period, studentId);
        SnapshotTrace trace = snapshotTrace(period, student, enrollment);
        String json = writeSnapshot(period, student, enrollment, calculation, attendance, conduct, trace);
        BulletinVersion transientVersion = new BulletinVersion();
        transientVersion.setState("PREVIEW");
        transientVersion.setSnapshotHash(sha256(json));
        transientVersion.setCalculationPolicy(period.getCalculationPolicy());
        transientVersion.setVersion(0);
        return view(transientVersion, period, student, calculation, attendance, conduct, trace);
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

    @Transactional
    public BulletinSnapshotView validate(UUID id) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Version de bulletin"));
        if (!"DRAFT".equals(version.getState()) && !"RETURNED".equals(version.getState())) throw ApiException.conflict("Cette version n'est plus un brouillon validable");
        windows.assertOpen(version.getReportingPeriodId(), AcademicWindowPolicyService.Action.VALIDATION);
        AcademicReportingPeriod period = period(version.getReportingPeriodId());
        Student student = students.findByIdAndSchoolId(version.getStudentId(), TenantContext.get()).orElseThrow();
        BulletinSnapshotView view = viewFromSnapshot(version, period, student);
        List<String> blockers = new ArrayList<>(view.blockers());
        if (view.conduct() == null || !"APPROVED".equalsIgnoreCase(view.conduct().status())) {
            blockers.add("CONDUCT_NOT_APPROVED");
        }
        if (!blockers.isEmpty()) throw ApiException.blockers("BULLETIN_NOT_READY",
                "Bulletin incomplet ou preuves administratives non approuvées : " + String.join("; ", blockers), blockers);
        version.setState("VALIDATED"); version.setValidatedAt(Instant.now()); version.setValidatedBy(currentUserId());
        versions.saveAndFlush(version);
        return viewFromSnapshot(version, period, student);
    }

    @Transactional
    public BulletinSnapshotView publish(UUID id, BulletinLifecycleRequest request) {
        BulletinVersion version = versions.findByIdAndSchoolId(id, TenantContext.get())
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
        return viewFromSnapshot(version, period, student);
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
        Map<UUID, String> names = jdbc.query("""
                SELECT e.student_id, s.last_name || ' ' || s.first_name
                  FROM student_enrollment e JOIN student s ON s.id=e.student_id
                 WHERE e.school_id=? AND e.academic_session_id=? AND e.school_class_id=? AND e.status='ACTIVE'
                """, rs -> {
            Map<UUID, String> result = new HashMap<>();
            while (rs.next()) result.put(rs.getObject(1, UUID.class), rs.getString(2));
            return result;
        }, TenantContext.get(), period.getAcademicSessionId(), classId);
        Map<UUID, FrozenPv> frozen = frozenPv(periodId, roster.stream().map(StudentEnrollment::getStudentId).toList());
        Map<UUID, Calculation> calculated = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : roster) {
            if (!frozen.containsKey(enrollment.getStudentId())) {
                calculated.put(enrollment.getStudentId(), calculatePeriod(enrollment.getStudentId(), period));
            }
        }
        List<BigDecimal> cohortAverages = new ArrayList<>();
        for (StudentEnrollment enrollment : roster) {
            FrozenPv saved = frozen.get(enrollment.getStudentId());
            BigDecimal average = saved == null ? calculated.get(enrollment.getStudentId()).average() : saved.average();
            List<String> blockers = saved == null ? calculated.get(enrollment.getStudentId()).blockers() : saved.blockers();
            if (blockers.isEmpty() && average != null) cohortAverages.add(average);
        }
        List<SessionPvRow> rows = new ArrayList<>();
        for (StudentEnrollment enrollment : roster) {
            UUID studentId = enrollment.getStudentId();
            FrozenPv saved = frozen.get(studentId);
            Calculation calculation = calculated.get(studentId);
            BigDecimal average = saved == null ? calculation.average() : saved.average();
            List<String> blockers = saved == null ? calculation.blockers() : saved.blockers();
            Integer rank = saved != null
                    ? saved.rank()
                    : blockers.isEmpty() && average != null
                    ? 1 + (int) cohortAverages.stream().filter(value -> value.compareTo(average) > 0).count() : null;
            rows.add(new SessionPvRow(saved == null ? null : saved.id(), studentId, names.getOrDefault(studentId, studentId.toString()),
                    average, rank, saved == null ? "PREVIEW" : saved.state(), blockers.isEmpty(), blockers));
        }
        rows.sort(Comparator
                .comparing(SessionPvRow::complete).reversed()
                .thenComparing(SessionPvRow::average, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SessionPvRow::studentName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        List<BigDecimal> completeAverages = rows.stream().filter(SessionPvRow::complete).map(SessionPvRow::average).toList();
        BigDecimal classAverage = completeAverages.isEmpty() ? BigDecimal.ZERO : completeAverages.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(completeAverages.size()), 2, RoundingMode.HALF_UP);
        return new SessionPvView(classId, schoolClass.getName(), period.getId(), period.getCode(), period.getLabel(), rows,
                classAverage, rows.size(), completeAverages.size());
    }

    private record FrozenPv(UUID id, UUID studentId, String state, BigDecimal average, Integer rank,
                            List<String> blockers) {}

    private Map<UUID, FrozenPv> frozenPv(UUID periodId, List<UUID> studentIds) {
        if (studentIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(studentIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(TenantContext.get()); args.add(periodId); args.addAll(studentIds);
        String sql = """
                SELECT DISTINCT ON (student_id) id,student_id,state,average,rank,snapshot_json
                  FROM bulletin_version
                 WHERE school_id=? AND reporting_period_id=? AND student_id IN (PLACEHOLDERS)
                   AND state IN ('PUBLISHED','VALIDATED')
                 ORDER BY student_id, CASE WHEN state='PUBLISHED' THEN 0 ELSE 1 END,
                          published_at DESC NULLS LAST, created_at DESC
                """.replace("PLACEHOLDERS", placeholders);
        return jdbc.query(sql, rs -> {
            Map<UUID, FrozenPv> result = new HashMap<>();
            while (rs.next()) {
                UUID studentId = rs.getObject("student_id", UUID.class);
                List<String> blockers;
                try {
                    var node = mapper.readTree(rs.getString("snapshot_json")).path("blockers");
                    List<String> parsed = new ArrayList<>();
                    if (node.isArray()) node.forEach(item -> parsed.add(item.asText()));
                    blockers = parsed;
                } catch (Exception ignored) {
                    blockers = List.of("SNAPSHOT_UNREADABLE");
                }
                result.put(studentId, new FrozenPv(rs.getObject("id", UUID.class), studentId, rs.getString("state"),
                        rs.getBigDecimal("average"), (Integer) rs.getObject("rank"), blockers));
            }
            return result;
        }, args.toArray());
    }

    private Calculation calculatePeriod(UUID studentId, AcademicReportingPeriod period) {
        if ("SEQUENCE".equals(period.getPeriodType())) return calculateSequence(studentId, period);
        List<DependencyRow> dependencies = dependencies(period);
        Map<String, List<BigDecimal>> bySubject = new LinkedHashMap<>();
        Map<String, List<PeriodMarkView>> componentMarks = new LinkedHashMap<>();
        Map<String, List<String>> componentRemarks = new LinkedHashMap<>();
        Map<String, Map<String, List<String>>> childBlockers = new LinkedHashMap<>();
        List<String> blockers = new ArrayList<>();
        Map<String, DependencyRow> byCode = dependencies.stream().collect(Collectors.toMap(
                d -> d.childCode().toUpperCase(Locale.ROOT), d -> d, (first, ignored) -> first, LinkedHashMap::new));
        for (DependencyRow dependency : dependencies) {
            BulletinVersion frozen = frozenChild(studentId, dependency.childPeriodId());
            if (frozen == null) {
                if (!dependency.optional()) blockers.add(dependency.childCode() + ":FROZEN_SNAPSHOT_REQUIRED");
                continue;
            }
            SnapshotPayload child = readPayload(frozen);
            List<String> childEvidenceBlockers = child.blockers() == null ? List.of() : child.blockers();
            if (!childEvidenceBlockers.isEmpty() && !dependency.optional()) {
                blockers.add(dependency.childCode() + " : " + String.join(", ", childEvidenceBlockers));
            }
            for (BulletinLineView line : child.lines() == null ? List.<BulletinLineView>of() : child.lines()) {
                bySubject.computeIfAbsent(line.subjectCode(), k -> new ArrayList<>()).add(line.mark());
                componentMarks.computeIfAbsent(line.subjectCode(), k -> new ArrayList<>()).add(new PeriodMarkView(dependency.childCode(), line.mark()));
                if (line.teacherRemark() != null && !line.teacherRemark().isBlank()) componentRemarks.computeIfAbsent(line.subjectCode(), k -> new ArrayList<>()).add(line.teacherRemark());
                childBlockers.computeIfAbsent(line.subjectCode(), k -> new LinkedHashMap<>()).put(dependency.childCode(), childEvidenceBlockers);
            }
        }
        Map<String, Integer> coefficients = effectiveCoefficients(studentId, period.getAcademicSessionId());
        List<BulletinLineView> lines = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> e : bySubject.entrySet()) {
            Map<String, List<String>> subjectChildBlockers = childBlockers.getOrDefault(e.getKey(), Map.of());
            Map<String, BigDecimal> subjectChildValues = new LinkedHashMap<>();
            for (PeriodMarkView component : componentMarks.getOrDefault(e.getKey(), List.of())) {
                subjectChildValues.put(component.periodCode(), component.mark());
            }
            AcademicCalculationEngine.Result result = "TERM_RESULT".equals(period.getPeriodType())
                    ? AcademicCalculationEngine.term(
                        childResult(subjectChildValues, subjectChildBlockers, "S1", AcademicCalculationEngine.Product.SEQUENCE), weight(byCode, "S1", BigDecimal.ONE),
                        childResult(subjectChildValues, subjectChildBlockers, "S2", AcademicCalculationEngine.Product.SEQUENCE), weight(byCode, "S2", BigDecimal.ONE),
                        subjectChildValues.containsKey("COMP") ? childResult(subjectChildValues, subjectChildBlockers, "COMP", AcademicCalculationEngine.Product.SEQUENCE) : null,
                        weight(byCode, "COMP", BigDecimal.ONE))
                    : AcademicCalculationEngine.annual(
                        childResult(subjectChildValues, subjectChildBlockers, "T1_RESULT", AcademicCalculationEngine.Product.TERM), weight(byCode, "T1_RESULT", BigDecimal.ONE),
                        childResult(subjectChildValues, subjectChildBlockers, "T2_RESULT", AcademicCalculationEngine.Product.TERM), weight(byCode, "T2_RESULT", BigDecimal.ONE),
                        childResult(subjectChildValues, subjectChildBlockers, "T3_RESULT", AcademicCalculationEngine.Product.TERM), weight(byCode, "T3_RESULT", BigDecimal.ONE));
            for (String blocker : result.blockers()) addDistinct(blockers, e.getKey() + ":" + blocker);
            BigDecimal mark = result.exempt() ? null : result.value();
            Subject subject = subjects.findBySchoolIdAndCode(TenantContext.get(), e.getKey()).orElse(null);
            int coef = coefficients.getOrDefault(e.getKey(), subject == null ? 1 : subject.getCoef());
            String remark = componentRemarks.getOrDefault(e.getKey(), List.of()).stream().reduce((first, last) -> last).orElse(null);
            CurriculumMetadata metadata = curriculumMetadata(studentId, period.getAcademicSessionId(), e.getKey());
            if (metadata.remarkRequired() && (remark == null || remark.isBlank())) addDistinct(blockers, e.getKey() + ":REMARK_REQUIRED");
            lines.add(new BulletinLineView(e.getKey(), subjectLabel(subject, e.getKey()), coef, mark,
                    mark == null ? null : mark.multiply(BigDecimal.valueOf(coef)), remark, appreciation(mark), List.of(),
                    componentMarks.getOrDefault(e.getKey(), List.of()), metadata.teacherName(), metadata.groupCode(), metadata.groupLabel()));
        }
        if (lines.isEmpty()) blockers.add("Aucune note calculable dans les périodes précédentes");
        return finish(lines, blockers, studentId, period);
    }

    private AcademicCalculationEngine.Result childResult(Map<String, BigDecimal> values,
                                                          Map<String, List<String>> blockers,
                                                          String code,
                                                          AcademicCalculationEngine.Product product) {
        BigDecimal value = values.get(code);
        List<String> childBlockers = blockers.getOrDefault(code, List.of());
        if (value == null) return new AcademicCalculationEngine.Result(
                product, null, BigDecimal.ZERO,
                childBlockers, List.of());
        return new AcademicCalculationEngine.Result(product,
                value, BigDecimal.ONE, childBlockers, List.of(code));
    }

    private List<DependencyRow> dependencies(AcademicReportingPeriod period) {
        return jdbc.query("""
                SELECT d.parent_period_id,d.child_period_id,child.code,d.weight,d.optional,d.display_order
                  FROM academic_reporting_period_dependency d
                  JOIN academic_reporting_period child ON child.id=d.child_period_id
                 WHERE d.school_id=? AND d.academic_session_id=? AND d.parent_period_id=?
                 ORDER BY d.display_order,child.code
                """, (rs, n) -> new DependencyRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getBigDecimal(4), rs.getBoolean(5), rs.getInt(6)),
                TenantContext.get(), period.getAcademicSessionId(), period.getId());
    }

    private BigDecimal weight(Map<String, DependencyRow> byCode, String code, BigDecimal fallback) {
        DependencyRow row = byCode.get(code.toUpperCase(Locale.ROOT));
        return row == null || row.weight() == null ? fallback : row.weight();
    }

    /** Only accepted/validated child products may feed a term or Annual product. */
    private BulletinVersion frozenChild(UUID studentId, UUID periodId) {
        UUID school = TenantContext.get();
        BulletinVersion published = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                school, studentId, periodId, "PUBLISHED").orElse(null);
        if (published != null) return published;
        return versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(school, studentId, periodId)
                .filter(v -> "VALIDATED".equals(v.getState())).orElse(null);
    }

    private SnapshotPayload readPayload(BulletinVersion version) {
        try {
            return mapper.readValue(version.getSnapshotJson(), SnapshotPayload.class);
        } catch (Exception ex) {
            throw ApiException.conflict("Le snapshot enfant " + version.getId() + " est illisible");
        }
    }

    private static void addDistinct(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
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
        for (String subjectCode : subjectCodes) {
            List<AcademicAssessment> applicable = definition.stream()
                    .filter(a -> a.getSubjectCode() == null || a.getSubjectCode().equalsIgnoreCase(subjectCode))
                    .sorted(Comparator.comparingInt(AcademicAssessment::getDisplayOrder).thenComparing(AcademicAssessment::getCode))
                    .toList();
            Map<UUID, AcademicGrade> gradesByAssessment = bySubject.getOrDefault(subjectCode, List.of()).stream()
                    .collect(Collectors.toMap(AcademicGrade::getAssessmentId, g -> g, (first, last) -> last));
            List<AcademicCalculationEngine.AssessmentInput> inputs = new ArrayList<>();
            List<AssessmentEvidenceView> evidence = new ArrayList<>();
            for (AcademicAssessment assessment : applicable) {
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
            if (applicable.isEmpty()) addDistinct(blockers, subjectCode + ":ASSESSMENT_NOT_CONFIGURED");
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
        if (definition.isEmpty()) blockers.add("ASSESSMENT_DEFINITIONS_MISSING");
        if (lines.isEmpty()) blockers.add("NO_SUBJECT_RESULT");
        return finish(lines, blockers, studentId, period);
    }

    /** Retained temporarily as a read-only compatibility reference during migration. */
    private Calculation calculateSequenceLegacy(UUID studentId, AcademicReportingPeriod period) {
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        UUID classId = enrollment == null ? null : enrollment.getSchoolClassId();
        List<AcademicAssessment> definition = classId == null
                ? assessments.findBySchoolIdAndReportingPeriodIdOrderByDisplayOrder(TenantContext.get(), period.getId())
                : assessments.findApplicableForClass(TenantContext.get(), period.getId(), classId);
        List<AcademicGrade> recorded = grades.findBySchoolIdAndStudentIdAndReportingPeriodIdOrderBySubjectCodeAscAssessmentIdAsc(TenantContext.get(), studentId, period.getId());
        Map<UUID, AcademicAssessment> byAssessment = new HashMap<>(); definition.forEach(a -> byAssessment.put(a.getId(), a));
        Map<String, List<AcademicGrade>> bySubject = new LinkedHashMap<>(); recorded.forEach(g -> bySubject.computeIfAbsent(g.getSubjectCode(), k -> new ArrayList<>()).add(g));
        List<BulletinLineView> lines = new ArrayList<>(); List<String> blockers = new ArrayList<>();
        Map<String, Integer> coefficients = effectiveCoefficients(studentId, period.getAcademicSessionId());
        for (AcademicAssessment a : definition) {
            if (!a.isMandatory()) continue;
            if (a.getSubjectCode() != null) {
                boolean present = recorded.stream().anyMatch(g -> g.getAssessmentId().equals(a.getId())
                        && !"MISSING".equals(g.getValueStatus())
                        && a.getSubjectCode().equalsIgnoreCase(g.getSubjectCode()));
                if (!present) blockers.add("\u00c9valuation obligatoire manquante : " + a.getSubjectCode() + " : " + a.getLabel());
                continue;
            }
            if (a.isMandatory() && recorded.stream().noneMatch(g -> g.getAssessmentId().equals(a.getId()) && !"MISSING".equals(g.getValueStatus()))) blockers.add("Évaluation obligatoire manquante : " + a.getLabel());
        }
        for (Map.Entry<String, List<AcademicGrade>> e : bySubject.entrySet()) {
            Map<UUID, AcademicAssessment> scopedByAssessment = new HashMap<>();
            definition.stream()
                    .filter(a -> a.getSubjectCode() == null || a.getSubjectCode().equalsIgnoreCase(e.getKey()))
                    .forEach(a -> scopedByAssessment.put(a.getId(), a));
            BigDecimal numerator = BigDecimal.ZERO, denominator = BigDecimal.ZERO; List<AssessmentEvidenceView> evidence = new ArrayList<>();
            for (AcademicGrade g : e.getValue()) {
                AcademicAssessment a = scopedByAssessment.get(g.getAssessmentId());
                if (a == null) continue;
                evidence.add(new AssessmentEvidenceView(a.getCode(), a.getLabel(), g.getMark(), a.getMaxScore(), a.getWeight(), g.getValueStatus()));
                if ("SCORED".equals(g.getValueStatus())) {
                    numerator = numerator.add(g.getMark().divide(a.getMaxScore(), 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(20)).multiply(a.getWeight()));
                    denominator = denominator.add(a.getWeight());
                }
            }
            BigDecimal mark = denominator.signum() == 0 ? BigDecimal.ZERO : numerator.divide(denominator, 4, RoundingMode.HALF_UP);
            Subject subject = subjects.findBySchoolIdAndCode(TenantContext.get(), e.getKey()).orElse(null);
            int coef = coefficients.getOrDefault(e.getKey(), subject == null ? 1 : subject.getCoef());
            var comment = comments.findBySchoolIdAndStudentIdAndReportingPeriodIdAndSubjectCode(TenantContext.get(), studentId, period.getId(), e.getKey()).orElse(null);
            CurriculumMetadata metadata = curriculumMetadata(studentId, period.getAcademicSessionId(), e.getKey());
            String teacherRemark = comment == null ? null : comment.getComment();
            if (metadata.remarkRequired() && (teacherRemark == null || teacherRemark.isBlank())) addDistinct(blockers, e.getKey() + ":REMARK_REQUIRED");
            lines.add(new BulletinLineView(e.getKey(), subjectLabel(subject, e.getKey()), coef, mark, mark.multiply(BigDecimal.valueOf(coef)), teacherRemark, appreciation(mark), evidence, List.of(new PeriodMarkView(period.getCode(), mark)), metadata.teacherName(), metadata.groupCode(), metadata.groupLabel()));
        }
        if (definition.isEmpty()) blockers.add("Aucune évaluation n'est configurée pour cette séquence");
        if (lines.isEmpty()) blockers.add("Aucune note saisie");
        return finish(lines, blockers, studentId, period);
    }

    private Calculation finish(List<BulletinLineView> lines, List<String> blockers, UUID studentId, AcademicReportingPeriod period) {
        BigDecimal weighted = BigDecimal.ZERO, coefs = BigDecimal.ZERO;
        for (BulletinLineView l : lines) {
            if (l.mark() == null || l.weighted() == null) continue;
            weighted = weighted.add(l.weighted());
            coefs = coefs.add(BigDecimal.valueOf(l.coefficient()));
        }
        BigDecimal average = coefs.signum() == 0 ? BigDecimal.ZERO : weighted.divide(coefs, 12, RoundingMode.HALF_UP);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow();
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        List<StudentEnrollment> classRoster = enrollment == null || enrollment.getSchoolClassId() == null ? List.of() : enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(TenantContext.get(), period.getAcademicSessionId(), enrollment.getSchoolClassId(), "ACTIVE");
        int rank = 1; int classSize = classRoster.size();
        return new Calculation(lines, blockers, average, rank, classSize,
                enrollment == null ? null : enrollment.getLevelSnapshot(),
                enrollment == null ? null : enrollment.getSubsystemSnapshot(),
                enrollment == null ? null : enrollment.getClassNameSnapshot(), null);
    }

    /** Calculate class statistics from the same server-side formula as the student. */
    private Calculation withClassStatistics(UUID studentId, AcademicReportingPeriod period, Calculation own) {
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        if (enrollment == null || enrollment.getSchoolClassId() == null) return own;
        List<StudentEnrollment> roster = enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                TenantContext.get(), period.getAcademicSessionId(), enrollment.getSchoolClassId(), "ACTIVE");
        List<Calculation> eligible = new ArrayList<>();
        for (StudentEnrollment peer : roster) {
            Calculation c = calculatePeriod(peer.getStudentId(), period);
            if (c.blockers().isEmpty()) eligible.add(c);
        }
        if (eligible.isEmpty()) return new Calculation(own.lines(), own.blockers(), own.average(), null, roster.size(), own.educationalLevel(), own.subsystem(), own.className(),
                new ClassStatsView(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0));
        List<BigDecimal> averages = eligible.stream().map(Calculation::average).filter(Objects::nonNull).sorted().toList();
        BigDecimal sum = averages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal classAverage = sum.divide(BigDecimal.valueOf(averages.size()), 12, RoundingMode.HALF_UP);
        BigDecimal minimum = averages.get(0), maximum = averages.get(averages.size() - 1);
        int successCount = (int) averages.stream().filter(x -> x.compareTo(BigDecimal.TEN) >= 0).count();
        BigDecimal successRate = BigDecimal.valueOf(successCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(averages.size()), 2, RoundingMode.HALF_UP);
        Integer rank = own.blockers().isEmpty() && own.average() != null ? 1 + (int) averages.stream().filter(x -> x.compareTo(own.average()) > 0).count() : null;
        ClassStatsView stats = new ClassStatsView(classAverage, minimum, maximum, successCount, successRate, averages.size());
        return new Calculation(own.lines(), own.blockers(), own.average(), rank, roster.size(), own.educationalLevel(), own.subsystem(), own.className(), stats);
    }

    private String writeSnapshot(AcademicReportingPeriod p, Student s, StudentEnrollment e, Calculation c,
                                 AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace) {
        try {
            return mapper.writeValueAsString(new SnapshotPayload(p.getCode(), p.getLabel(), p.getPeriodType(),
                    s.getId(), s.getMatricule(), s.getLastName() + " " + s.getFirstName(),
                    c.educationalLevel(), c.subsystem(), e.getClassNameSnapshot(), c.lines(), c.average(), c.rank(), c.classSize(), c.blockers(),
                    p.getCalculationPolicy(), attendance, conduct, c.classStats(), groupStats(c.lines()), trace));
        } catch (JsonProcessingException ex) {
            throw ApiException.conflict("Impossible de créer le snapshot du bulletin");
        }
    }
    private BulletinSnapshotView view(BulletinVersion v, AcademicReportingPeriod p, Student s, Calculation c,
                                      AttendanceSummaryView attendance, ConductSummaryView conduct, SnapshotTrace trace) {
        return new BulletinSnapshotView(v.getId(), p.getAcademicSessionId(), p.getId(), p.getCode(), p.getLabel(),
                s.getId(), s.getLastName() + " " + s.getFirstName(), s.getMatricule(), c.educationalLevel(), c.subsystem(), c.className(), c.lines(),
                c.average(), c.rank(), c.classSize(), v.getState(), c.blockers().isEmpty(), c.blockers(),
                v.getSnapshotHash(), v.getCalculationPolicy(), v.getGeneralAppreciation(), attendance, conduct,
                v.getVersion(), c.classStats(), v.getSupersedesId(), v.getCorrectsBulletinVersionId(),
                v.getCorrectionReason(), v.getCorrectionRequestedBy(), v.getCorrectionRequestedAt(),
                groupStats(c.lines()), evidence(trace));
    }
    private BulletinSnapshotView viewFromSnapshot(BulletinVersion v, AcademicReportingPeriod p, Student s) {
        try {
            SnapshotPayload x = mapper.readValue(v.getSnapshotJson(), SnapshotPayload.class);
            return new BulletinSnapshotView(v.getId(), p.getAcademicSessionId(), p.getId(), p.getCode(), p.getLabel(),
                    s.getId(), x.studentName(), x.matricule(), x.educationalLevel(), x.subsystem(), x.className(), x.lines(), x.average(), x.rank(),
                    x.classSize(), v.getState(), x.blockers().isEmpty(), x.blockers(), v.getSnapshotHash(),
                    v.getCalculationPolicy(), v.getGeneralAppreciation(), x.attendance(), x.conduct(), v.getVersion(),
                    x.classStats(), v.getSupersedesId(), v.getCorrectsBulletinVersionId(), v.getCorrectionReason(),
                    v.getCorrectionRequestedBy(), v.getCorrectionRequestedAt(),
                    x.groupStats() == null ? groupStats(x.lines()) : x.groupStats(), evidence(x.trace()));
        } catch (Exception ex) {
            throw ApiException.conflict("Snapshot de bulletin illisible");
        }
    }
    private record Calculation(List<BulletinLineView> lines, List<String> blockers, BigDecimal average, Integer rank, int classSize, String educationalLevel, String subsystem, String className, ClassStatsView classStats) {}
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
                subjectAssignments, homeroomAssignments, childSnapshotTrace(period, student.getId()),
                "AcademicCalculationEngine/v1", period.getCalculationPolicy(), profilePhoto, documentDesign);
    }
    private record SnapshotPayload(String periodCode, String periodLabel, String periodType, UUID studentId, String matricule, String studentName, String educationalLevel, String subsystem, String className, List<BulletinLineView> lines, BigDecimal average, Integer rank, int classSize, List<String> blockers, String calculationPolicy, AttendanceSummaryView attendance, ConductSummaryView conduct, ClassStatsView classStats, List<GroupStatsView> groupStats, SnapshotTrace trace) {}
    private record SnapshotTrace(UUID reportingPeriodId, long reportingPeriodVersion, UUID enrollmentId, UUID classId,
                                 List<CurriculumTrace> curriculum, List<AssessmentTrace> assessments,
                                 List<AssignmentTrace> subjectAssignments, List<AssignmentTrace> homeroomAssignments,
                                 List<ChildSnapshotTrace> childSnapshots,
                                 String formulaVersion, String calculationPolicy,
                                 ProfileAssetTrace profilePhoto, DocumentDesignTrace documentDesign) {}
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
        return new SnapshotEvidenceView(photo, design, children, trace.formulaVersion(), trace.calculationPolicy());
    }

    private String templateReference(SnapshotTrace trace) {
        if (trace == null || trace.documentDesign() == null) return null;
        DocumentDesignTrace design = trace.documentDesign();
        String template = design.templateId() == null ? "none" : design.templateId() + ":v" + design.templateVersion();
        String branding = design.brandingId() == null ? "none" : design.brandingId() + ":v" + design.brandingVersion();
        return "template=" + template + ";branding=" + branding;
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
                            WHEN product=? AND template_family='REFERENCE' AND subsystem=? THEN 0
                            WHEN product=? AND template_family='GENERIC' THEN 1
                            WHEN product='GENERIC' AND template_family='GENERIC' THEN 2
                            ELSE 3 END,
                          template_version DESC
                 LIMIT 1
                """, (rs, n) -> new TemplateCandidate(rs.getObject("id", UUID.class), rs.getString("template_family"),
                        rs.getString("product"), rs.getString("locale"), rs.getInt("template_version"),
                        rs.getString("body_template")), schoolId, locale, product, subsystem, product, subsystem, product);
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

    private List<ChildSnapshotTrace> childSnapshotTrace(AcademicReportingPeriod period, UUID studentId) {
        if ("SEQUENCE".equals(period.getPeriodType())) return List.of();
        return dependencies(period).stream().map(dependency -> {
            BulletinVersion frozen = frozenChild(studentId, dependency.childPeriodId());
            return frozen == null ? null : new ChildSnapshotTrace(dependency.childPeriodId(), dependency.childCode(),
                    frozen.getId(), frozen.getVersion(), frozen.getState(), frozen.getSnapshotHash());
        }).filter(Objects::nonNull).toList();
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
