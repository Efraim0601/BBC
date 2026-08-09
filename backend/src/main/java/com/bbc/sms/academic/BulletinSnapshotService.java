package com.bbc.sms.academic;

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
import java.util.*;

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
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public BulletinSnapshotService(AcademicReportingPeriodRepository periods, AcademicAssessmentRepository assessments,
                                   AcademicGradeRepository grades, SubjectResultCommentRepository comments,
                                   BulletinVersionRepository versions, StudentEnrollmentRepository enrollments,
                                   StudentRepository students, SubjectRepository subjects,
                                   SubjectClassCoefRepository subjectClassCoefs, SchoolClassRepository classes,
                                   AcademicWindowPolicyService windows, TeacherScopeService teacherScope, ObjectMapper mapper,
                                   JdbcTemplate jdbc) {
        this.periods = periods; this.assessments = assessments; this.grades = grades; this.comments = comments;
        this.versions = versions; this.enrollments = enrollments; this.students = students; this.subjects = subjects;
        this.subjectClassCoefs = subjectClassCoefs; this.classes = classes;
        this.windows = windows; this.teacherScope = teacherScope; this.mapper = mapper; this.jdbc = jdbc;
    }

    @Transactional
    public BulletinSnapshotView calculate(UUID studentId, UUID periodId) {
        teacherScope.assertStudent(studentId);
        AcademicReportingPeriod period = period(periodId);
        Student student = students.findByIdAndSchoolId(studentId, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Élève"));
        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, period.getAcademicSessionId(), "ACTIVE").orElse(null);
        if (enrollment == null) throw ApiException.conflict("Aucune inscription active pour cet élève dans cette session");
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
        String json = writeSnapshot(period, student, enrollment, calculation, attendance, conduct);
        BulletinVersion version = new BulletinVersion();
        version.setSchoolId(TenantContext.get()); version.setAcademicSessionId(period.getAcademicSessionId()); version.setReportingPeriodId(periodId);
        version.setStudentId(studentId); version.setEnrollmentId(enrollment.getId()); version.setState("DRAFT"); version.setSnapshotJson(json);
        version.setSnapshotHash(sha256(json)); version.setAverage(calculation.average()); version.setRank(calculation.rank()); version.setClassSize(calculation.classSize());
        version.setCalculationPolicy(period.getCalculationPolicy()); version.setCreatedBy(currentUserId());
        return view(versions.save(version), period, student, calculation, attendance, conduct);
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
        String json = writeSnapshot(period, student, enrollment, calculation, attendance, conduct);
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
        return view(versions.save(replacement), period, student, calculation, attendance, conduct);
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
        if (!view.complete()) throw ApiException.conflict("Bulletin incomplet : " + String.join("; ", view.blockers()));
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
        List<SessionPvRow> rows = new ArrayList<>();
        for (StudentEnrollment enrollment : roster) {
            BulletinSnapshotView snapshot = calculate(enrollment.getStudentId(), periodId);
            rows.add(new SessionPvRow(snapshot.id(), snapshot.studentId(), snapshot.studentName(), snapshot.average(),
                    snapshot.rank(), snapshot.state(), snapshot.complete(), snapshot.blockers()));
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

    private Calculation calculatePeriod(UUID studentId, AcademicReportingPeriod period) {
        if ("SEQUENCE".equals(period.getPeriodType())) return calculateSequence(studentId, period);
        List<AcademicReportingPeriod> children = periods.findBySchoolIdAndAcademicSessionIdOrderByDisplayOrder(TenantContext.get(), period.getAcademicSessionId()).stream()
                .filter(p -> ("TERM_RESULT".equals(period.getPeriodType()) && "SEQUENCE".equals(p.getPeriodType()) && Objects.equals(p.getAcademicTermId(), period.getAcademicTermId()))
                        || ("ANNUAL_RESULT".equals(period.getPeriodType()) && "TERM_RESULT".equals(p.getPeriodType())))
                .toList();
        Map<String, List<BigDecimal>> bySubject = new LinkedHashMap<>();
        Map<String, List<PeriodMarkView>> componentMarks = new LinkedHashMap<>();
        Map<String, List<String>> componentRemarks = new LinkedHashMap<>();
        List<String> blockers = new ArrayList<>();
        for (AcademicReportingPeriod child : children) {
            Calculation c = calculatePeriod(studentId, child);
            if (!c.blockers().isEmpty()) blockers.add(child.getCode() + " : " + String.join(", ", c.blockers()));
            for (BulletinLineView line : c.lines()) {
                bySubject.computeIfAbsent(line.subjectCode(), k -> new ArrayList<>()).add(line.mark());
                componentMarks.computeIfAbsent(line.subjectCode(), k -> new ArrayList<>()).add(new PeriodMarkView(child.getCode(), line.mark()));
                if (line.teacherRemark() != null && !line.teacherRemark().isBlank()) componentRemarks.computeIfAbsent(line.subjectCode(), k -> new ArrayList<>()).add(line.teacherRemark());
            }
        }
        Map<String, Integer> coefficients = effectiveCoefficients(studentId, period.getAcademicSessionId());
        List<BulletinLineView> lines = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> e : bySubject.entrySet()) {
            BigDecimal mark = e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(e.getValue().size()), 4, RoundingMode.HALF_UP);
            Subject subject = subjects.findBySchoolIdAndCode(TenantContext.get(), e.getKey()).orElse(null);
            int coef = coefficients.getOrDefault(e.getKey(), subject == null ? 1 : subject.getCoef());
            String remark = componentRemarks.getOrDefault(e.getKey(), List.of()).stream().reduce((first, last) -> last).orElse(null);
            CurriculumMetadata metadata = curriculumMetadata(studentId, period.getAcademicSessionId(), e.getKey());
            lines.add(new BulletinLineView(e.getKey(), subjectLabel(subject, e.getKey()), coef, mark, mark.multiply(BigDecimal.valueOf(coef)), remark, appreciation(mark), List.of(), componentMarks.getOrDefault(e.getKey(), List.of()), metadata.teacherName(), metadata.groupCode(), metadata.groupLabel()));
        }
        if (lines.isEmpty()) blockers.add("Aucune note calculable dans les périodes précédentes");
        return finish(lines, blockers, studentId, period);
    }

    private Calculation calculateSequence(UUID studentId, AcademicReportingPeriod period) {
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
            lines.add(new BulletinLineView(e.getKey(), subjectLabel(subject, e.getKey()), coef, mark, mark.multiply(BigDecimal.valueOf(coef)), comment == null ? null : comment.getComment(), appreciation(mark), evidence, List.of(new PeriodMarkView(period.getCode(), mark)), metadata.teacherName(), metadata.groupCode(), metadata.groupLabel()));
        }
        if (definition.isEmpty()) blockers.add("Aucune évaluation n'est configurée pour cette séquence");
        if (lines.isEmpty()) blockers.add("Aucune note saisie");
        return finish(lines, blockers, studentId, period);
    }

    private Calculation finish(List<BulletinLineView> lines, List<String> blockers, UUID studentId, AcademicReportingPeriod period) {
        BigDecimal weighted = BigDecimal.ZERO, coefs = BigDecimal.ZERO; for (BulletinLineView l : lines) { weighted = weighted.add(l.weighted()); coefs = coefs.add(BigDecimal.valueOf(l.coefficient())); }
        BigDecimal average = coefs.signum() == 0 ? BigDecimal.ZERO : weighted.divide(coefs, 4, RoundingMode.HALF_UP);
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
        List<BigDecimal> averages = eligible.stream().map(Calculation::average).map(x -> x.setScale(2, RoundingMode.HALF_UP)).sorted().toList();
        BigDecimal sum = averages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal classAverage = sum.divide(BigDecimal.valueOf(averages.size()), 2, RoundingMode.HALF_UP);
        BigDecimal minimum = averages.get(0), maximum = averages.get(averages.size() - 1);
        int successCount = (int) averages.stream().filter(x -> x.compareTo(BigDecimal.TEN) >= 0).count();
        BigDecimal successRate = BigDecimal.valueOf(successCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(averages.size()), 2, RoundingMode.HALF_UP);
        Integer rank = own.blockers().isEmpty() ? 1 + (int) averages.stream().filter(x -> x.compareTo(own.average().setScale(2, RoundingMode.HALF_UP)) > 0).count() : null;
        ClassStatsView stats = new ClassStatsView(classAverage, minimum, maximum, successCount, successRate, averages.size());
        return new Calculation(own.lines(), own.blockers(), own.average(), rank, roster.size(), own.educationalLevel(), own.subsystem(), own.className(), stats);
    }

    private String writeSnapshot(AcademicReportingPeriod p, Student s, StudentEnrollment e, Calculation c, AttendanceSummaryView attendance, ConductSummaryView conduct) {
        try {
            return mapper.writeValueAsString(new SnapshotPayload(p.getCode(), p.getLabel(), p.getPeriodType(),
                    s.getId(), s.getMatricule(), s.getLastName() + " " + s.getFirstName(),
                    c.educationalLevel(), c.subsystem(), e.getClassNameSnapshot(), c.lines(), c.average(), c.rank(), c.classSize(), c.blockers(),
                    p.getCalculationPolicy(), attendance, conduct, c.classStats(), groupStats(c.lines())));
        } catch (JsonProcessingException ex) {
            throw ApiException.conflict("Impossible de créer le snapshot du bulletin");
        }
    }
    private BulletinSnapshotView view(BulletinVersion v, AcademicReportingPeriod p, Student s, Calculation c, AttendanceSummaryView attendance, ConductSummaryView conduct) {
        return new BulletinSnapshotView(v.getId(), p.getAcademicSessionId(), p.getId(), p.getCode(), p.getLabel(),
                s.getId(), s.getLastName() + " " + s.getFirstName(), s.getMatricule(), c.educationalLevel(), c.subsystem(), c.className(), c.lines(),
                c.average(), c.rank(), c.classSize(), v.getState(), c.blockers().isEmpty(), c.blockers(),
                v.getSnapshotHash(), v.getCalculationPolicy(), v.getGeneralAppreciation(), attendance, conduct,
                v.getVersion(), c.classStats(), v.getSupersedesId(), v.getCorrectsBulletinVersionId(),
                v.getCorrectionReason(), v.getCorrectionRequestedBy(), v.getCorrectionRequestedAt(),
                groupStats(c.lines()));
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
                    x.groupStats() == null ? groupStats(x.lines()) : x.groupStats());
        } catch (Exception ex) {
            throw ApiException.conflict("Snapshot de bulletin illisible");
        }
    }
    private record Calculation(List<BulletinLineView> lines, List<String> blockers, BigDecimal average, Integer rank, int classSize, String educationalLevel, String subsystem, String className, ClassStatsView classStats) {}
    private record SnapshotPayload(String periodCode, String periodLabel, String periodType, UUID studentId, String matricule, String studentName, String educationalLevel, String subsystem, String className, List<BulletinLineView> lines, BigDecimal average, Integer rank, int classSize, List<String> blockers, String calculationPolicy, AttendanceSummaryView attendance, ConductSummaryView conduct, ClassStatsView classStats, List<GroupStatsView> groupStats) {}
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
     * Subject.coef is the catalog default; a stored subject_class_coef row for
     * the class is the authoritative override used on the bulletin.
     */
    private Map<String, Integer> effectiveCoefficients(UUID studentId, UUID sessionId) {
        UUID schoolId = TenantContext.get();
        List<Subject> catalog = subjects.findBySchoolIdOrderByCode(schoolId);
        Map<String, Integer> byCode = new HashMap<>();
        Map<UUID, String> codeBySubjectId = new HashMap<>();
        for (Subject subject : catalog) {
            byCode.put(subject.getCode(), subject.getCoef());
            codeBySubjectId.put(subject.getId(), subject.getCode());
        }

        // The session curriculum is authoritative; the legacy class override
        // remains only as a compatibility fallback for sessions not migrated yet.
        jdbc.query("SELECT s.code, c.coefficient FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=?",
                rs -> { while (rs.next()) byCode.put(rs.getString(1), rs.getInt(2)); return null; },
                schoolId, sessionId, enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(schoolId, studentId, sessionId, "ACTIVE").map(StudentEnrollment::getSchoolClassId).orElse(null));

        StudentEnrollment enrollment = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                schoolId, studentId, sessionId, "ACTIVE").orElse(null);
        if (enrollment == null) return byCode;

        SchoolClass schoolClass = enrollment.getSchoolClassId() == null
                ? classes.findBySchoolIdAndName(schoolId, enrollment.getClassNameSnapshot()).orElse(null)
                : classes.findByIdAndSchoolId(enrollment.getSchoolClassId(), schoolId).orElse(null);
        if (schoolClass == null) return byCode;

        for (SubjectClassCoef override : subjectClassCoefs.findBySchoolIdAndClassId(schoolId, schoolClass.getId())) {
            String code = codeBySubjectId.get(override.getSubjectId());
            if (code != null) byCode.put(code, override.getCoef());
        }
        return byCode;
    }
    private CurriculumMetadata curriculumMetadata(UUID studentId, UUID sessionId, String subjectCode) {
        UUID classId = enrollments.findFirstBySchoolIdAndStudentIdAndAcademicSessionIdAndStatus(
                TenantContext.get(), studentId, sessionId, "ACTIVE").map(StudentEnrollment::getSchoolClassId).orElse(null);
        if (classId == null) return new CurriculumMetadata(null, null, null);
        return jdbc.query("SELECT g.code, COALESCE(g.label->>'fr',g.label->>'en',g.code), e.name "
                        + "FROM academic_curriculum_subject c JOIN subject s ON s.id=c.subject_id "
                        + "LEFT JOIN academic_subject_group g ON g.id=c.group_id "
                        + "LEFT JOIN LATERAL (SELECT ast.employee_id FROM academic_class_subject_teacher ast "
                        + "WHERE ast.school_id=? AND ast.academic_session_id=? AND ast.class_id=? AND ast.subject_id=c.subject_id "
                        + "AND ast.active=true ORDER BY CASE ast.role WHEN 'RESPONSIBLE' THEN 0 WHEN 'HOMEROOM' THEN 1 ELSE 2 END, ast.created_at LIMIT 1) ast ON true "
                        + "LEFT JOIN employee e ON e.id=ast.employee_id WHERE c.school_id=? AND c.academic_session_id=? AND c.class_id=? AND s.code=?",
                rs -> rs.next() ? new CurriculumMetadata(rs.getString(1), rs.getString(2), rs.getString(3)) : new CurriculumMetadata(null, null, null),
                TenantContext.get(), sessionId, classId, TenantContext.get(), sessionId, classId, subjectCode);
    }
    private record CurriculumMetadata(String groupCode, String groupLabel, String teacherName) {}
    private String appreciation(BigDecimal a){return a.compareTo(BigDecimal.valueOf(16))>=0?"Excellent":a.compareTo(BigDecimal.valueOf(14))>=0?"Très bien":a.compareTo(BigDecimal.valueOf(12))>=0?"Bien":a.compareTo(BigDecimal.valueOf(10))>=0?"Acquis":"En cours d'acquisition";}
    private AttendanceSummaryView attendance(AcademicReportingPeriod p, UUID studentId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT count(DISTINCT s.id) AS finalized_sessions, count(*) FILTER (WHERE m.status='PRESENT') AS present_count, count(*) FILTER (WHERE m.status='ABSENT') AS absent_count, count(*) FILTER (WHERE m.status='EXCUSED') AS excused_count, count(*) FILTER (WHERE m.status='LATE') AS late_count, coalesce(sum(m.late_minutes),0) AS late_minutes, coalesce(sum(CASE WHEN m.status='EXCUSED' THEN s.duration_minutes ELSE 0 END),0) / 60.0 AS justified_absence_hours, coalesce(sum(CASE WHEN m.status='ABSENT' THEN s.duration_minutes ELSE 0 END),0) / 60.0 AS unjustified_absence_hours FROM attendance_session s JOIN attendance_mark m ON m.attendance_session_id=s.id WHERE s.school_id=? AND s.academic_session_id=? AND m.student_id=? AND s.status='FINALIZED' AND s.session_date BETWEEN ? AND ?", TenantContext.get(), p.getAcademicSessionId(), studentId, p.getStartDate(), p.getEndDate());
        int finalized = ((Number) row.getOrDefault("finalized_sessions", 0)).intValue(); int present = ((Number) row.getOrDefault("present_count", 0)).intValue(); int absent = ((Number) row.getOrDefault("absent_count", 0)).intValue(); int excused = ((Number) row.getOrDefault("excused_count", 0)).intValue(); int late = ((Number) row.getOrDefault("late_count", 0)).intValue(); int lateMinutes = ((Number) row.getOrDefault("late_minutes", 0)).intValue();
        Map<String, Object> adjustment = jdbc.queryForMap("SELECT coalesce(sum(justified_absence_hours),0) AS justified, coalesce(sum(unjustified_absence_hours),0) AS unjustified, coalesce(sum(late_minutes),0) AS late_minutes FROM attendance_period_adjustment WHERE school_id=? AND reporting_period_id=? AND student_id=? AND status='APPROVED'", TenantContext.get(), p.getId(), studentId);
        return new AttendanceSummaryView(finalized, present, absent, excused, late, lateMinutes, new BigDecimal(row.get("justified_absence_hours").toString()), new BigDecimal(row.get("unjustified_absence_hours").toString()), new BigDecimal(adjustment.get("justified").toString()), new BigDecimal(adjustment.get("unjustified").toString()), ((Number) adjustment.get("late_minutes")).intValue());
    }
    private ConductSummaryView conduct(AcademicReportingPeriod p, UUID studentId) { return jdbc.query("SELECT work_warning,work_blame,conduct_warning,conduct_blame,honor_roll,encouragement,congratulations,exclusion_days,decision_code,council_observation,status FROM student_period_conduct WHERE school_id=? AND reporting_period_id=? AND student_id=?", rs -> rs.next() ? new ConductSummaryView(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11)) : new ConductSummaryView(false,false,false,false,false,false,false,0,null,null,"DRAFT"), TenantContext.get(), p.getId(), studentId); }
    private String sha256(String v){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private UUID currentUserId(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getPrincipal() instanceof com.bbc.sms.platform.security.AppUserPrincipal p?p.userId():null;}
}
