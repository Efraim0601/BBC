package com.bbc.sms.academic;

import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Deterministic class-level ZIP output with a machine-readable manifest. */
@Service
public class ReportCardBatchService {
    private final BulletinVersionRepository versions;
    private final StudentEnrollmentRepository enrollments;
    private final AcademicReportingPeriodRepository periods;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final BulletinSnapshotService snapshots;
    private final ReportCardPdfService pdf;
    private final TeacherScopeService teacherScope;

    public ReportCardBatchService(BulletinVersionRepository versions,
                                   StudentEnrollmentRepository enrollments,
                                   AcademicReportingPeriodRepository periods,
                                   StudentRepository students,
                                   SchoolClassRepository classes,
                                   BulletinSnapshotService snapshots,
                                   ReportCardPdfService pdf,
                                   TeacherScopeService teacherScope) {
        this.versions = versions;
        this.enrollments = enrollments;
        this.periods = periods;
        this.students = students;
        this.classes = classes;
        this.snapshots = snapshots;
        this.pdf = pdf;
        this.teacherScope = teacherScope;
    }

    @Transactional(readOnly = true)
    public byte[] render(UUID classId, UUID periodId, String locale) {
        teacherScope.assertClass(classId);
        classes.findByIdAndSchoolId(classId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Classe"));
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        List<StudentEnrollment> roster = enrollments.findBySchoolIdAndAcademicSessionIdAndSchoolClassIdAndStatusOrderByClassNameSnapshotAsc(
                TenantContext.get(), period.getAcademicSessionId(), classId, "ACTIVE");
        if (roster.isEmpty()) throw ApiException.conflict("Aucun élève actif dans cette classe");
        boolean french = !"en".equalsIgnoreCase(locale);
        List<String> manifest = new ArrayList<>();
        manifest.add("student_id,student_name,status,file,sha256,size_bytes,snapshot_id,snapshot_version,snapshot_hash,document_id,error");
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : roster) {
            BulletinVersion version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                    TenantContext.get(), enrollment.getStudentId(), periodId, "PUBLISHED").orElse(null);
            if (version == null) version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(
                    TenantContext.get(), enrollment.getStudentId(), periodId).filter(v -> "VALIDATED".equals(v.getState())).orElse(null);
            String name;
            try { name = version == null ? students.findByIdAndSchoolId(enrollment.getStudentId(), TenantContext.get()).map(s -> s.getLastName() + " " + s.getFirstName()).orElse(enrollment.getStudentId().toString()) : snapshots.byId(version.getId()).studentName(); }
            catch (Exception ignored) { name = enrollment.getStudentId().toString(); }
            if (version == null) {
                manifest.add(csv(enrollment.getStudentId().toString(), name, "BLOCKED", "", "", "", "", "", "", "", "No validated or published snapshot"));
                continue;
            }
            try {
                byte[] bytes = pdf.render(version.getId(), french);
                String file = safeFile(name) + "-" + enrollment.getStudentId().toString().substring(0, 8) + ".pdf";
                files.put(file, bytes);
                manifest.add(csv(enrollment.getStudentId().toString(), name, version.getState(), file, sha256(bytes), String.valueOf(bytes.length),
                        version.getId().toString(), String.valueOf(version.getVersion()), version.getSnapshotHash(), "", ""));
            } catch (Exception ex) {
                manifest.add(csv(enrollment.getStudentId().toString(), name, "ERROR", "", "", "", version.getId().toString(), String.valueOf(version.getVersion()), version.getSnapshotHash(), "", clip(ex.getMessage())));
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("manifest.csv"));
            zip.write(String.join("\n", manifest).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de créer l'archive des bulletins", ex);
        }
    }

    private static String csv(String... values) {
        return Arrays.stream(values).map(v -> "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"").reduce((a, b) -> a + "," + b).orElse("");
    }
    private static String safeFile(String value) { return value == null ? "student" : value.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_"); }
    private static String clip(String value) { return value == null ? "Generation failed" : value.replace('\n', ' ').replace('\r', ' ').replace(',', ';').substring(0, Math.min(180, value.length())); }
    private static String sha256(byte[] bytes) {
        try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format("%02x", b)); return out.toString(); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
