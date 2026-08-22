package com.bbc.sms.academic;

import com.bbc.sms.foundation.enrollment.StudentEnrollment;
import com.bbc.sms.foundation.enrollment.StudentEnrollmentRepository;
import com.bbc.sms.foundation.cohort.AcademicCohortResolver;
import com.bbc.sms.foundation.session.AcademicReportingPeriod;
import com.bbc.sms.foundation.session.AcademicReportingPeriodRepository;
import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.academic.dto.AcademicDtos.BulletinSnapshotView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

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
    private final AcademicAccessPolicyService accessPolicy;
    private final AcademicCohortResolver cohorts;

    public ReportCardBatchService(BulletinVersionRepository versions,
                                   StudentEnrollmentRepository enrollments,
                                   AcademicReportingPeriodRepository periods,
                                   StudentRepository students,
                                   SchoolClassRepository classes,
                                   BulletinSnapshotService snapshots,
                                   ReportCardPdfService pdf,
                                   AcademicAccessPolicyService accessPolicy,
                                   AcademicCohortResolver cohorts) {
        this.versions = versions;
        this.enrollments = enrollments;
        this.periods = periods;
        this.students = students;
        this.classes = classes;
        this.snapshots = snapshots;
        this.pdf = pdf;
        this.accessPolicy = accessPolicy;
        this.cohorts = cohorts;
    }

    @Transactional(readOnly = true)
    public byte[] render(UUID classId, UUID periodId, String locale) {
        classes.findByIdAndSchoolId(classId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Classe"));
        AcademicReportingPeriod period = periods.findByIdAndSchoolId(periodId, TenantContext.get())
                .orElseThrow(() -> ApiException.notFound("Période de résultat"));
        accessPolicy.require(AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW,
                period.getAcademicSessionId(), classId, null, null, period.getStartDate());
        List<UUID> roster = cohorts.rosterStudentIds(period.getAcademicSessionId(), classId, "ACTIVE", period.getStartDate());
        if (roster.isEmpty()) throw ApiException.conflict("Aucun élève actif dans cette classe");
        boolean french = !"en".equalsIgnoreCase(locale);
        List<String> manifest = new ArrayList<>();
        manifest.add("student_id,student_name,status,file,sha256,size_bytes,snapshot_id,snapshot_version,snapshot_hash,document_id,error");
        Map<String, byte[]> files = new LinkedHashMap<>();
        List<BulletinSnapshotView> frozen = new ArrayList<>();
        for (UUID studentId : roster) {
            BulletinVersion version = classVersion(studentId, classId, period.getAcademicSessionId(), periodId);
            String name;
            try { name = version == null ? students.findByIdAndSchoolId(studentId, TenantContext.get()).map(s -> s.getLastName() + " " + s.getFirstName()).orElse(studentId.toString()) : snapshots.byId(version.getId()).studentName(); }
            catch (Exception ignored) { name = studentId.toString(); }
            if (version == null) {
                manifest.add(csv(studentId.toString(), name, "BLOCKED", "", "", "", "", "", "", "", "No validated or published snapshot"));
                continue;
            }
            try {
                byte[] bytes = pdf.render(version.getId(), french);
                BulletinSnapshotView snapshot = snapshots.byId(version.getId());
                frozen.add(snapshot);
                String file = safeFile(name) + "-" + studentId.toString().substring(0, 8) + ".pdf";
                files.put(file, bytes);
                manifest.add(csv(studentId.toString(), name, version.getState(), file, sha256(bytes), String.valueOf(bytes.length),
                        version.getId().toString(), String.valueOf(version.getVersion()), version.getSnapshotHash(), "", ""));
            } catch (Exception ex) {
                manifest.add(csv(studentId.toString(), name, "ERROR", "", "", "", version.getId().toString(), String.valueOf(version.getVersion()), version.getSnapshotHash(), "", clip(ex.getMessage())));
            }
        }
        addCompanions(files, manifest, frozen, classId, period, french);
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

    private BulletinVersion classVersion(UUID studentId, UUID classId, UUID sessionId, UUID periodId) {
        BulletinVersion version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndProgrammeClassIdAndStateOrderByPublishedAtDesc(
                TenantContext.get(), studentId, periodId, classId, "PUBLISHED").orElse(null);
        if (version != null) return version;
        version = versions.findBySchoolIdAndStudentIdAndReportingPeriodIdAndProgrammeClassIdAndState(
                        TenantContext.get(), studentId, periodId, classId, "VALIDATED").stream()
                .max(Comparator.comparing(BulletinVersion::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (version != null || cohorts.cohortForClass(sessionId, classId) != null) return version;
        version = versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdAndStateOrderByPublishedAtDesc(
                TenantContext.get(), studentId, periodId, "PUBLISHED").orElse(null);
        if (version != null) return version;
        return versions.findFirstBySchoolIdAndStudentIdAndReportingPeriodIdOrderByCreatedAtDesc(
                TenantContext.get(), studentId, periodId)
                .filter(v -> "VALIDATED".equals(v.getState())).orElse(null);
    }

    private void addCompanions(Map<String, byte[]> files, List<String> manifest,
                                List<BulletinSnapshotView> snapshots, UUID classId,
                                AcademicReportingPeriod period, boolean french) {
        List<BulletinSnapshotView> honored = snapshots.stream()
                .filter(x -> x.conduct() != null && x.conduct().honorRoll()).toList();
        for (BulletinSnapshotView student : honored) {
            String file = "honor-roll/" + safeFile(student.studentName()) + "-certificate.pdf";
            byte[] bytes = companionPdf(french ? "TABLEAU D'HONNEUR" : "HONOR ROLL",
                    List.of(student.studentName(), french ? "Classe : " + student.className() : "Class: " + student.className(),
                            french ? "Moyenne : " + number(student.average()) + " / 20" : "Average: " + number(student.average()) + " / 20",
                            french ? "Distinction du conseil de classe" : "Class council distinction"));
            files.put(file, bytes); manifest.add(companionManifest(file, bytes, "HONOR_CERTIFICATE"));
        }
        List<String> stats = new ArrayList<>();
        stats.add((french ? "STATISTIQUES DE LA CLASSE " : "CLASS STATISTICS ") + period.getLabel());
        if (!snapshots.isEmpty() && snapshots.get(0).classStats() != null) {
            var x = snapshots.get(0).classStats();
            stats.add("Average: " + number(x.average()) + " / 20");
            stats.add("Minimum: " + number(x.minimum()) + " · Maximum: " + number(x.maximum()));
            stats.add("Pass rate: " + number(x.successRate()) + "% · Ranked: " + x.rankedCount());
        }
        String statsFile = "class-statistics.pdf"; byte[] statsPdf = companionPdf(stats.get(0), stats.subList(1, stats.size()));
        files.put(statsFile, statsPdf); manifest.add(companionManifest(statsFile, statsPdf, "CLASS_STATISTICS"));
        List<String> pv = new ArrayList<>(); pv.add((french ? "PROCES VERBAL / REGISTRE " : "CLASS PV / REGISTER ") + period.getLabel());
        snapshots.stream().sorted(Comparator.comparing(BulletinSnapshotView::studentName, String.CASE_INSENSITIVE_ORDER))
                .forEach(x -> pv.add(x.studentName() + " | " + number(x.average()) + " | " + (x.rank() == null ? "-" : x.rank()) + " | " + x.state()));
        String pvFile = "pv-register.pdf"; byte[] pvPdf = companionPdf(pv.get(0), pv.subList(1, pv.size()));
        files.put(pvFile, pvPdf); manifest.add(companionManifest(pvFile, pvPdf, "PV_REGISTER"));
    }

    private String companionManifest(String file, byte[] bytes, String kind) {
        return csv("", kind, "COMPANION", file, sha256(bytes), String.valueOf(bytes.length), "", "", "", "", "");
    }

    private static byte[] companionPdf(String title, List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 15); cs.newLineAtOffset(52, 790); cs.showText(pdfSafe(title));
                cs.setFont(PDType1Font.HELVETICA, 10);
                for (String line : lines) { cs.newLineAtOffset(0, -18); cs.showText(pdfSafe(line)); }
                cs.endText();
            }
            document.save(out); return out.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("Companion document generation failed", ex); }
    }
    private static String pdfSafe(String value) { return value == null ? "" : value.replace('é','e').replace('è','e').replace('ê','e').replace('à','a').replace('ù','u').replace('ô','o').replace('î','i').replace('ç','c'); }
    private static String number(java.math.BigDecimal value) { return value == null ? "-" : value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }

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
