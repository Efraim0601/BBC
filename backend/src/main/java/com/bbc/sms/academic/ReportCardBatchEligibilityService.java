package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchRepairTarget;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchSnapshotEvidence;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchReasonCount;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchPreviewView;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinBatchWindowView;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView;
import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.foundation.session.AcademicWindowPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.timetable.SchoolClass;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single source of truth for official batch eligibility.
 *
 * <p>The worker, preflight, creation and recovery paths all use this service.
 * A report-card PDF is eligible only when the exact active enrollment for the
 * selected class/session has an exact-period readable PUBLISHED snapshot.
 */
@Service
public class ReportCardBatchEligibilityService {
    public static final String POLICY = "PUBLISHED_ONLY";
    public static final String POLICY_VERSION = "PUBLISHED_ONLY_V1";

    private final JdbcTemplate jdbc;
    private final StudentEnrollmentRepository enrollments;
    private final AcademicReportingPeriodRepository periods;
    private final AcademicSessionRepository sessions;
    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final TeacherScopeService teacherScope;
    private final BulletinSnapshotService snapshots;
    private final ObjectMapper mapper;
    private final AcademicWindowPolicyService windows;

    public ReportCardBatchEligibilityService(JdbcTemplate jdbc,
                                             StudentEnrollmentRepository enrollments,
                                             AcademicReportingPeriodRepository periods,
                                             AcademicSessionRepository sessions,
                                             SchoolClassRepository classes,
                                             StudentRepository students,
                                             TeacherScopeService teacherScope,
                                             BulletinSnapshotService snapshots,
                                             ObjectMapper mapper,
                                             AcademicWindowPolicyService windows) {
        this.jdbc = jdbc;
        this.enrollments = enrollments;
        this.periods = periods;
        this.sessions = sessions;
        this.classes = classes;
        this.students = students;
        this.teacherScope = teacherScope;
        this.snapshots = snapshots;
        this.mapper = mapper;
        this.windows = windows;
    }

    @Transactional(readOnly = true)
    public EligibilityPreview preview(UUID classId, UUID reportingPeriodId, String locale) {
        UUID schoolId = TenantContext.get();
        if (classId == null || reportingPeriodId == null) {
            throw ApiException.fields(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "BATCH_SCOPE_REQUIRED", "La classe et la période de résultat sont obligatoires.",
                    Map.of(classId == null ? "classId" : "reportingPeriodId",
                            classId == null ? "La classe est obligatoire." : "La période est obligatoire."));
        }
        teacherScope.assertClass(classId);
        SchoolClass schoolClass = classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(reportingPeriodId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        AcademicWindowPolicyService.WindowView window = windows.effective(
                period.getId(), AcademicWindowPolicyService.Action.BATCH_GENERATION);
        var session = sessions.findByIdAndSchoolId(period.getAcademicSessionId(), schoolId)
                .orElseThrow(() -> ApiException.notFound("Session académique"));
        List<StudentEnrollment> roster = enrollments
                .findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                        schoolId, period.getAcademicSessionId(), classId, "ACTIVE");
        if (roster.isEmpty()) {
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, "BATCH_EMPTY_ROSTER",
                    "Aucun élève actif dans cette classe pour la session sélectionnée.");
        }
        String normalizedLocale = normalizeLocale(locale);
        List<EligibilityRow> rows = roster.stream()
                .map(enrollment -> resolveForEnrollment(schoolId, schoolClass, period, enrollment, normalizedLocale))
                .toList();
        BulletinBatchWindowView windowView = windowView(window);
        return new EligibilityPreview(POLICY, POLICY_VERSION, period.getAcademicSessionId(), session.getLabel(),
                classId, schoolClass.getName(), period.getId(), period.getCode(), period.getLabel(),
                rows, fingerprint(schoolId, schoolClass, period, rows), window.serverTime(), normalizedLocale, windowView);
    }

    /** Resolve one row from the current active enrollment without broadening tenant scope. */
    @Transactional(readOnly = true)
    public EligibilityRow resolveForJob(UUID schoolId, UUID sessionId, UUID classId,
                                        UUID periodId, UUID studentId, String locale) {
        if (!schoolId.equals(TenantContext.get())) throw ApiException.forbidden("Accès locataire invalide");
        StudentEnrollment enrollment = enrollments
                .findBySchoolIdAndStudentIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByEnrolledOnDescCreatedAtDesc(
                        schoolId, studentId, sessionId, classId, "ACTIVE")
                .stream().findFirst().orElse(null);
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        SchoolClass schoolClass = classes.findByIdAndSchoolId(classId, schoolId)
                .orElseThrow(() -> ApiException.notFound("Classe"));
        if (enrollment == null) {
            Student student = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
            return blocker(schoolClass, period, studentId, studentName(student, studentId),
                    student == null ? "" : student.getMatricule(), BulletinBatchResultCode.ENROLLMENT_MISSING,
                    "NONE", null, normalizeLocale(locale));
        }
        return resolveForEnrollment(schoolId, schoolClass, period, enrollment, normalizeLocale(locale));
    }

    private EligibilityRow resolveForEnrollment(UUID schoolId, SchoolClass schoolClass,
                                                AcademicReportingPeriod period,
                                                StudentEnrollment enrollment, String locale) {
        UUID studentId = enrollment.getStudentId();
        Student student = students.findByIdAndSchoolId(studentId, schoolId).orElse(null);
        String name = studentName(student, studentId);
        String matricule = student == null || student.getMatricule() == null ? "" : student.getMatricule();
        List<VersionRow> versions = jdbc.query("""
                SELECT id,enrollment_id,state,snapshot_hash,version,created_at,published_at,snapshot_json::text
                  FROM bulletin_version
                 WHERE school_id=? AND academic_session_id=? AND reporting_period_id=?
                   AND student_id=? AND enrollment_id=?
                 ORDER BY created_at DESC,id DESC
                """, (rs, rowNum) -> version(rs), schoolId, period.getAcademicSessionId(), period.getId(),
                studentId, enrollment.getId());
        return classify(schoolId, schoolClass, period, enrollment, name, matricule, versions, locale);
    }

    private EligibilityRow classify(UUID schoolId, SchoolClass schoolClass,
                                    AcademicReportingPeriod period, StudentEnrollment enrollment,
                                    String name, String matricule, List<VersionRow> versions, String locale) {
        List<VersionRow> published = versions.stream().filter(v -> "PUBLISHED".equals(v.state()))
                .sorted(versionRecency()).toList();
        if (!published.isEmpty()) {
            VersionRow candidate = published.stream().filter(this::readable).findFirst().orElse(null);
            if (candidate == null) {
                return technical(schoolClass, period, published.get(0), enrollment.getStudentId(), name, matricule,
                        BulletinBatchResultCode.SNAPSHOT_UNREADABLE, locale);
            }
            return ready(schoolClass, period, enrollment.getStudentId(), name, matricule, candidate);
        }

        VersionRow current = versions.stream()
                .filter(v -> List.of("DRAFT", "TEACHER_SUBMITTED", "REVIEW", "RETURNED", "VALIDATED").contains(v.state()))
                .sorted(versionRecency()).findFirst().orElse(null);
        if (current != null) {
            if (!readable(current)) {
                return technical(schoolClass, period, current, enrollment.getStudentId(), name, matricule,
                        BulletinBatchResultCode.SNAPSHOT_UNREADABLE, locale);
            }
            BulletinBatchResultCode code = switch (current.state()) {
                case "RETURNED" -> BulletinBatchResultCode.REPORT_RETURNED;
                case "VALIDATED" -> BulletinBatchResultCode.REPORT_VALIDATED_NOT_PUBLISHED;
                default -> BulletinBatchResultCode.REPORT_DRAFT;
            };
            if (code == BulletinBatchResultCode.REPORT_DRAFT || code == BulletinBatchResultCode.REPORT_RETURNED) {
                try {
                    BulletinSnapshotView live = snapshots.preview(enrollment.getStudentId(), period.getId());
                    String currentSourceHash = live == null || live.workflowMeta() == null
                            ? null : live.workflowMeta().currentSourceHash();
                    if (currentSourceHash != null && current.snapshotHash() != null
                            && !currentSourceHash.equals(current.snapshotHash())) {
                        code = BulletinBatchResultCode.REPORT_STALE;
                    }
                } catch (RuntimeException ignored) {
                    // Eligibility must still explain the persisted lifecycle state if
                    // a current calculation cannot be loaded during a read-only preview.
                }
            }
            return blocker(schoolClass, period, enrollment.getStudentId(), name, matricule, code,
                    current.state(), evidence(current), locale);
        }
        if (versions.stream().anyMatch(v -> "REVOKED".equals(v.state()))) {
            VersionRow revoked = versions.stream().filter(v -> "REVOKED".equals(v.state()))
                    .sorted(versionRecency()).findFirst().orElse(null);
            return blocker(schoolClass, period, enrollment.getStudentId(), name, matricule,
                    BulletinBatchResultCode.REPORT_PUBLICATION_REVOKED, "REVOKED", evidence(revoked), locale);
        }
        if (versions.stream().anyMatch(v -> "SUPERSEDED".equals(v.state()))) {
            VersionRow superseded = versions.stream().filter(v -> "SUPERSEDED".equals(v.state()))
                    .sorted(versionRecency()).findFirst().orElse(null);
            return blocker(schoolClass, period, enrollment.getStudentId(), name, matricule,
                    BulletinBatchResultCode.REPORT_SUPERSEDED_ONLY, "SUPERSEDED", evidence(superseded), locale);
        }
        return blocker(schoolClass, period, enrollment.getStudentId(), name, matricule,
                BulletinBatchResultCode.REPORT_NOT_CREATED, "NONE", null, locale);
    }

    private EligibilityRow ready(SchoolClass schoolClass, AcademicReportingPeriod period, UUID studentId,
                                 String name, String matricule, VersionRow candidate) {
        return new EligibilityRow(studentId, name, matricule, "READY", BulletinBatchResultCode.PUBLISHED.name(),
                BulletinBatchResultCode.PUBLISHED.category(), BulletinBatchResultCode.PUBLISHED.messageKey(),
                messageArgs(period, name, matricule), "PUBLISHED", false, null, evidence(candidate), null);
    }

    private EligibilityRow blocker(SchoolClass schoolClass, AcademicReportingPeriod period, UUID studentId,
                                   String name, String matricule, BulletinBatchResultCode code, String state,
                                   BulletinBatchSnapshotEvidence snapshot, String locale) {
        BulletinBatchRepairTarget repair = repairTarget(schoolClass, period, studentId);
        return new EligibilityRow(studentId, name, matricule, "BLOCKED", code.name(), code.category(),
                code.messageKey(), messageArgs(period, name, matricule), state, false, repair, snapshot, null);
    }

    private EligibilityRow technical(SchoolClass schoolClass, AcademicReportingPeriod period, VersionRow candidate,
                                     UUID studentId, String name, String matricule,
                                     BulletinBatchResultCode code, String locale) {
        return new EligibilityRow(studentId, name, matricule, "BLOCKED", code.name(), code.category(),
                code.messageKey(), messageArgs(period, name, matricule), candidate == null ? "UNKNOWN" : candidate.state(),
                code.retryableByDefault(), repairTarget(schoolClass, period, studentId), evidence(candidate), null);
    }

    private String fingerprint(UUID schoolId, SchoolClass schoolClass, AcademicReportingPeriod period,
                               List<EligibilityRow> rows) {
        String roster = rows.stream().map(row -> row.studentId() + "|" + row.eligibility() + "|" + row.code()
                        + "|" + (row.snapshot() == null ? "" : row.snapshot().id()) + "|"
                        + (row.snapshot() == null ? "" : row.snapshot().version()) + "|"
                        + (row.snapshot() == null ? "" : row.snapshot().hash()) + "|"
                        + (row.snapshot() == null ? "" : row.snapshot().publishedAt()))
                .sorted().collect(Collectors.joining(";"));
        String input = POLICY_VERSION + "|" + schoolId + "|" + period.getAcademicSessionId() + "|"
                + schoolClass.getId() + "|" + period.getId() + "|" + period.getVersion() + "|" + roster;
        try {
            return HexFormatHolder.sha256(input);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fingerprint batch scope", ex);
        }
    }

    private BulletinBatchRepairTarget repairTarget(SchoolClass schoolClass, AcademicReportingPeriod period, UUID studentId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("mode", "bulletin");
        query.put("classId", String.valueOf(schoolClass.getId()));
        query.put("reportingPeriodId", String.valueOf(period.getId()));
        query.put("studentId", String.valueOf(studentId));
        return new BulletinBatchRepairTarget("/academic", query);
    }

    private Map<String, Object> messageArgs(AcademicReportingPeriod period, String studentName, String matricule) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("periodCode", period.getCode());
        args.put("periodLabel", period.getLabel());
        args.put("studentName", studentName);
        args.put("matricule", matricule);
        return args;
    }

    private static Comparator<VersionRow> versionRecency() {
        return Comparator.comparing(VersionRow::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(VersionRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(VersionRow::id, Comparator.reverseOrder());
    }

    private boolean readable(VersionRow row) {
        if (row == null || row.snapshotJson() == null || row.snapshotJson().isBlank()) return false;
        try {
            JsonNode node = mapper.readTree(row.snapshotJson());
            return node != null && node.isObject();
        } catch (Exception ignored) { return false; }
    }

    private static BulletinBatchSnapshotEvidence evidence(VersionRow row) {
        return row == null ? null : new BulletinBatchSnapshotEvidence(row.id(), row.version(), row.snapshotHash(),
                row.publishedAt(), row.state());
    }

    private VersionRow version(ResultSet rs) throws java.sql.SQLException {
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("enrollment_id", UUID.class),
                rs.getString("state"), rs.getString("snapshot_hash"), rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "published_at"), rs.getString("snapshot_json"));
    }

    private static Instant instant(ResultSet rs, String column) throws java.sql.SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String studentName(Student student, UUID id) {
        return student == null ? String.valueOf(id) : (student.getLastName() + " " + student.getFirstName()).trim();
    }

    private static String normalizeLocale(String locale) { return "en".equalsIgnoreCase(locale) ? "en" : "fr"; }

    public static String legacyCode(String resultCode, String error) {
        if (resultCode != null && !resultCode.isBlank() && !"REPORT_NOT_PUBLISHED_LEGACY".equals(resultCode)) return resultCode;
        if (error != null && error.toLowerCase(Locale.ROOT).contains("no validated or published snapshot")) {
            return BulletinBatchResultCode.REPORT_NOT_CREATED.name();
        }
        return resultCode == null || resultCode.isBlank() ? BulletinBatchResultCode.UNEXPECTED_GENERATION_ERROR.name() : resultCode;
    }

    public record EligibilityPreview(String policy, String policyVersion, UUID academicSessionId,
                                     String academicSessionLabel, UUID classId, String className,
                                     UUID reportingPeriodId, String reportingPeriodCode,
                                      String reportingPeriodLabel, List<EligibilityRow> rows,
                                      String scopeFingerprint, Instant generatedAt, String locale,
                                      BulletinBatchWindowView window) {
        public int totalStudents() { return rows.size(); }
        public int readyStudents() { return (int) rows.stream().filter(row -> "READY".equals(row.eligibility())).count(); }
        public int blockedStudents() { return totalStudents() - readyStudents(); }
        public List<BulletinBatchReasonCount> reasonCounts() {
            Map<String, Long> counts = rows.stream().filter(row -> !"READY".equals(row.eligibility()))
                    .collect(Collectors.groupingBy(EligibilityRow::code, LinkedHashMap::new, Collectors.counting()));
            return counts.entrySet().stream().map(entry -> new BulletinBatchReasonCount(entry.getKey(), entry.getValue().intValue())).toList();
        }
        public BulletinBatchPreviewView view() {
            return new BulletinBatchPreviewView(policy, academicSessionId, academicSessionLabel, classId, className,
                    reportingPeriodId, reportingPeriodCode, reportingPeriodLabel, totalStudents(), readyStudents(),
                    blockedStudents(), reasonCounts(), rows.stream().map(EligibilityRow::view).toList(),
                    scopeFingerprint, generatedAt, window);
        }
    }

    private BulletinBatchWindowView windowView(AcademicWindowPolicyService.WindowView window) {
        BulletinBatchRepairTarget repair = new BulletinBatchRepairTarget("/settings", Map.of("tab", "sessions"));
        String state = "UNRESTRICTED".equals(window.effectiveMode()) ? "UNRESTRICTED" : window.state();
        return new BulletinBatchWindowView(state, window.open(), window.governingTermCode(),
                window.governingTermLabel(), window.governedPeriodCodes(), window.timezone(), window.serverTime(),
                window.opensAt(), window.closesAt(), window.nextTransition(), repair);
    }

    public record EligibilityRow(UUID studentId, String studentName, String matricule, String eligibility,
                                 String code, String category, String messageKey, Map<String, Object> messageArgs,
                                 String currentState, boolean retryableNow, BulletinBatchRepairTarget repairTarget,
                                 BulletinBatchSnapshotEvidence snapshot, String correlationId) {
        public BulletinBatchPreviewView.Row view() {
            return new BulletinBatchPreviewView.Row(studentId, studentName, matricule, eligibility, code, category,
                    messageKey, messageArgs, currentState, retryableNow, repairTarget, snapshot);
        }
    }

    private record VersionRow(UUID id, UUID enrollmentId, String state, String snapshotHash, long version,
                              Instant createdAt, Instant publishedAt, String snapshotJson) {}

    private static final class HexFormatHolder {
        private static String sha256(String value) {
            try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        }
    }
}
