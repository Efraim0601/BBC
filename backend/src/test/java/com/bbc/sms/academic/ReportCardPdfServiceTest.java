package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.media.ProfilePhotoRepository;
import com.bbc.sms.platform.tenant.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.bbc.sms.timetable.SchoolClassRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCardPdfServiceTest {
    @Mock BulletinSnapshotService snapshots;
    @Mock ProfilePhotoRepository photos;
    @Mock SchoolClassRepository classes;
    @Mock JdbcTemplate jdbc;

    @Test
    void rendersSecondaryA4WithLongCompetencyEvidenceAndNoPhoto() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        BulletinLineView line = new BulletinLineView(
                "MATH", "Mathematics", 4, new BigDecimal("14"), new BigDecimal("56"),
                "Excellent effort with a long teacher remark that must wrap inside the right-most column.",
                "Good", List.of(
                new AssessmentEvidenceView("UNDERSTAND", "Understand and mobilise programme concepts in a new situation", new BigDecimal("14"), new BigDecimal("20"), BigDecimal.ONE, "SCORED"),
                new AssessmentEvidenceView("APPLY", "Apply a rigorous method and communicate a justified result", new BigDecimal("14"), new BigDecimal("20"), BigDecimal.ONE, "SCORED")),
                List.of(new PeriodMarkView("S1", new BigDecimal("14"))), "MANGA Elise", null, null);
        BulletinSnapshotView view = new BulletinSnapshotView(
                snapshotId, UUID.randomUUID(), UUID.randomUUID(), "S1", "Sequence 1",
                UUID.randomUUID(), "FOTSO Cédric", "BBC-1001", "secondary", "FR", "4ème",
                List.of(line), new BigDecimal("14"), 1, 1, "PUBLISHED", true, List.of(),
                "0123456789abcdef", "DEFAULT", null, null,
                new ConductSummaryView(false, false, false, false, true, true, false, 0,
                        "MEETS_EXPECTATIONS", "Reviewed", "APPROVED"), 1,
                new ClassStatsView(new BigDecimal("14"), new BigDecimal("14"), new BigDecimal("14"), 1,
                        new BigDecimal("100"), 1), null, null, null, null, null, List.of(), null);
        when(snapshots.byId(snapshotId)).thenReturn(view);
        when(photos.findByOwnerTypeAndOwnerIdAndSchoolId("student", view.studentId(), schoolId))
                .thenReturn(Optional.empty());

        TenantContext.set(schoolId);
        byte[] bytes;
        try {
            bytes = new ReportCardPdfService(snapshots, photos, classes, jdbc).render(snapshotId, true);
        } finally {
            TenantContext.clear();
        }

        assertThat(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(595.27563f, org.assertj.core.data.Offset.offset(0.01f));
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("BULLETIN DE NOTES - SEQUENCE 1", "COMPETENCES EVALUEES", "N/20", "M/20");
        }
        writeQa("secondary-sequence-fr.pdf", bytes);
    }

    @Test
    void rendersSecondaryAnnualUsingTermColumnsInsteadOfGenericComputedLayout() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        BulletinLineView line = new BulletinLineView(
                "FR", "Francais", 3, new BigDecimal("13.5"), new BigDecimal("40.5"),
                "Assez bien", "Assez bien", List.of(),
                List.of(new PeriodMarkView("T1_RESULT", new BigDecimal("12")),
                        new PeriodMarkView("T2_RESULT", new BigDecimal("13.5")),
                        new PeriodMarkView("T3_RESULT", new BigDecimal("15"))),
                "MADIBA Rose", null, null);
        BulletinSnapshotView view = snapshot(snapshotId, line, "ANNUAL", "Resultat annuel", "ANNUAL_RESULT", "ANNUAL");
        when(snapshots.byId(snapshotId)).thenReturn(view);
        when(photos.findByOwnerTypeAndOwnerIdAndSchoolId("student", view.studentId(), schoolId))
                .thenReturn(Optional.empty());

        TenantContext.set(schoolId);
        byte[] bytes;
        try {
            bytes = new ReportCardPdfService(snapshots, photos, classes, jdbc).render(snapshotId, false);
        } finally {
            TenantContext.clear();
        }
        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ANNUAL REPORT SHEET", "T1", "T2", "T3", "PRODUCT", "[MIN-MAX]");
            assertThat(text).doesNotContain("WEIGHTED");
        }
        writeQa("secondary-annual-en.pdf", bytes);
    }

    @Test
    void rendersSecondaryTrimesterWithCompetencyAndIndividualMarkColumns() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        BulletinLineView line = new BulletinLineView(
                "MATH", "Mathematiques", 4, new BigDecimal("15"), new BigDecimal("60"),
                "Tres bien", "Tres bien",
                List.of(new AssessmentEvidenceView("S1", "Resolution de problemes", new BigDecimal("14"),
                                new BigDecimal("20"), BigDecimal.ONE, "SCORED"),
                        new AssessmentEvidenceView("S2", "Calcul numerique", new BigDecimal("16"),
                                new BigDecimal("20"), BigDecimal.ONE, "SCORED")),
                List.of(new PeriodMarkView("S1", new BigDecimal("14")),
                        new PeriodMarkView("S2", new BigDecimal("16"))),
                "TENEKU Donal", null, null);
        BulletinSnapshotView view = snapshot(snapshotId, line, "T1_RESULT", "Premier trimestre", "TERM_RESULT", "TERM");
        when(snapshots.byId(snapshotId)).thenReturn(view);
        when(photos.findByOwnerTypeAndOwnerIdAndSchoolId("student", view.studentId(), schoolId))
                .thenReturn(Optional.empty());

        TenantContext.set(schoolId);
        byte[] bytes;
        try {
            bytes = new ReportCardPdfService(snapshots, photos, classes, jdbc).render(snapshotId, true);
        } finally {
            TenantContext.clear();
        }
        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("BULLETIN SCOLAIRE - PREMIER TRIMESTRE",
                    "COMPETENCES EVALUEES", "N/20", "M/20", "Resolution de problemes", "14");
            assertThat(text).doesNotContain("PONDERE");
        }
        writeQa("secondary-term-fr.pdf", bytes);
    }

    private BulletinSnapshotView snapshot(UUID snapshotId, BulletinLineView line, String code, String label,
                                          String periodType, String product) {
        return new BulletinSnapshotView(
                snapshotId, UUID.randomUUID(), UUID.randomUUID(), code, label,
                UUID.randomUUID(), "MBOUENDE Jeanne", "BBC-1002", "secondary", "EN", "Form I",
                List.of(line), new BigDecimal("13.5"), 3, 28, "PUBLISHED", true, List.of(),
                "abcdef0123456789", "DEFAULT", null, null,
                new ConductSummaryView(false, false, false, false, false, true, false, 0,
                        "PROMOTED", "Good work", "APPROVED"), 1,
                new ClassStatsView(new BigDecimal("11.5"), new BigDecimal("5"), new BigDecimal("17"), 20,
                        new BigDecimal("71.43"), 28), null, null, null, null, null, List.of(), null,
                periodType, product, null, List.of());
    }

    private void writeQa(String fileName, byte[] bytes) throws Exception {
        String directory = System.getProperty("reportCard.qa.dir");
        if (directory == null || directory.isBlank()) return;
        Path target = Path.of(directory, fileName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
