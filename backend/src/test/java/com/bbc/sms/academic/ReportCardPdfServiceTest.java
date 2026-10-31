package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.media.ProfilePhotoRepository;
import com.bbc.sms.platform.tenant.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
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
import java.util.stream.IntStream;

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
            assertThat(text).contains("BULLETIN DE NOTES DE LA SEQUENCE 1", "COMPÉTENCES ÉVALUÉES", "N/20", "M/20", "TOTAL", "Moyenne");
            int images = 0;
            for (COSName name : document.getPage(0).getResources().getXObjectNames()) {
                if (document.getPage(0).getResources().getXObject(name) instanceof PDImageXObject) images++;
            }
            // The fallback BBC logo and the verification QR must both be embedded.
            assertThat(images).isGreaterThanOrEqualTo(2);
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
            assertThat(text).contains("ANNUAL REPORT SHEET", "T1", "T2", "T3", "PRODUCT", "MIN-MAX", "TERM AVERAGES", "Student average");
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
            assertThat(text).contains("FIRST TERM PROGRESS RECORD",
                    "COMPETENCIES EVALUATED", "N/20", "M/20", "Resolution de problemes", "14");
            assertThat(text).doesNotContain("PONDERE");
        }
        writeQa("secondary-term-fr.pdf", bytes);
    }

    @Test
    void rendersPrimaryWithoutCoefficientOrWeightedProductFields() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        BulletinLineView line = new BulletinLineView(
                "MATH", "Mathematiques", 8, new BigDecimal("15"), new BigDecimal("120"),
                "Tres bien", "Tres bien", List.of(),
                List.of(new PeriodMarkView("S1", new BigDecimal("15"))),
                "NGONO Aline", null, null);
        BulletinSnapshotView view = new BulletinSnapshotView(
                snapshotId, UUID.randomUUID(), UUID.randomUUID(), "T1_RESULT", "Premier trimestre",
                UUID.randomUUID(), "MBOUENDE Jeanne", "BBC-1003", "primary", "FR", "CE2 A",
                List.of(line), new BigDecimal("15"), 1, 26, "PUBLISHED", true, List.of(),
                "primary012345678", "DEFAULT", null, null,
                new ConductSummaryView(false, false, false, false, false, false, false, 0,
                        "PROMOTED", "Good work", "APPROVED"), 1,
                new ClassStatsView(new BigDecimal("13"), new BigDecimal("8"), new BigDecimal("18"), 21,
                        new BigDecimal("80"), 26), null, null, null, null, null, List.of(), null,
                "TERM_RESULT", "TERM", null, List.of());
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
            assertThat(text).contains("BULLETIN SCOLAIRE DU 1er TRIMESTRE", "Moyenne", "Total des notes");
            assertThat(text).doesNotContain("COEF", "PONDERE", "120");
        }
        writeQa("primary-term-fr-no-coefficients.pdf", bytes);
    }

    @Test
    void rendersSecondaryAnnualWithFifteenSubjectsOnASinglePage() throws Exception {
        List<BulletinLineView> lines = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> annualLine("SUBJECT" + i, "Subject " + i, "TEACHER " + i)).toList();
        byte[] bytes = renderAnnual(lines, "secondary", "EN", false);
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Subject 15", "TEACHER 15", "TERM AVERAGES", "Student average",
                    "STUDENT PERFORMANCE", "Parent / Guardian signature", "Scan to verify");
        }
        writeQa("secondary-annual-en-15-subjects.pdf", bytes);
    }

    @Test
    void rendersFrenchSecondaryAnnualOnASinglePage() throws Exception {
        List<BulletinLineView> lines = IntStream.rangeClosed(1, 13)
                .mapToObj(i -> annualLine("MATIERE" + i, "Matière " + i, "PROF " + i)).toList();
        byte[] bytes = renderAnnual(lines, "secondary", "FR", true);
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("BULLETIN SCOLAIRE ANNUEL", "Matière 13", "TRAVAIL DE L'ÉLÈVE",
                    "Décision du conseil de classe", "Scanner pour vérifier");
        }
        writeQa("secondary-annual-fr-13-subjects.pdf", bytes);
    }

    @Test
    void rendersPrimaryAnnualWithTwentyOneSubjectsOnASinglePage() throws Exception {
        List<BulletinLineView> lines = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> annualLine("SUBJECT" + i, "English Language — Component " + i, null)).toList();
        byte[] bytes = renderAnnual(lines, "primary", "EN", false);
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ANNUAL REPORT SHEET", "Component 21", "AVERAGE", "Total marks",
                    "Class council decision", "Scan to verify");
        }
        writeQa("primary-annual-en-21-subjects.pdf", bytes);
    }

    @Test
    void everyAnnualSubjectCountUpToTwentySixFitsOnOnePage() throws Exception {
        // Equal float row heights can sum past the budget by a rounding error;
        // 17, 19, 20, 22 and 24 subjects used to spill the summary onto page 2.
        for (String level : List.of("secondary", "primary")) {
            for (int count = 1; count <= 26; count++) {
                List<BulletinLineView> lines = IntStream.rangeClosed(1, count)
                        .mapToObj(i -> annualLine("SUBJECT" + i, "Subject " + i, "TEACHER " + i)).toList();
                try (PDDocument document = PDDocument.load(renderAnnual(lines, level, "EN", false))) {
                    assertThat(document.getNumberOfPages()).as(level + " with " + count + " subjects").isEqualTo(1);
                }
            }
        }
    }

    @Test
    void longFrenchPrimaryLabelsWrapInsteadOfBeingCut() throws Exception {
        List<String> subjects = List.of("Mathématiques — Géométrie et espace", "Mathématiques — Mesures et grandeurs",
                "Technologies de l'information et de la communication", "Français — Littérature / Lecture");
        List<BulletinLineView> lines = IntStream.range(0, 18)
                .mapToObj(i -> annualLine("SUBJECT" + i, subjects.get(i % subjects.size()), null)).toList();
        byte[] bytes = renderAnnual(lines, "primary", "FR", true);
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = flatText(document);
            assertThat(text).contains("Technologies de l'information et de la communication",
                    "Mathématiques — Mesures et grandeurs", "Nom et visa du professeur", "Promu(e) / Good work");
            assertThat(text).doesNotContain("...");
        }
        writeQa("primary-annual-fr-long-labels.pdf", bytes);
    }

    @Test
    void annualCardWithTooManySubjectsStillPaginatesInsteadOfOverlapping() throws Exception {
        List<BulletinLineView> lines = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> annualLine("SUBJECT" + i, "Subject " + i, "TEACHER " + i)).toList();
        try (PDDocument document = PDDocument.load(renderAnnual(lines, "secondary", "EN", false))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void longAnnualSubjectNamesWrapOntoTwoLinesOnOnePage() throws Exception {
        List<String> secondary = List.of("Éducation à la citoyenneté et à la morale",
                "Technologies de l'information et de la communication", "Économie sociale et familiale");
        List<BulletinLineView> secondaryLines = IntStream.range(0, 16)
                .mapToObj(i -> annualLine("SUBJECT" + i, secondary.get(i % secondary.size()), "NSAH ZOUFANE")).toList();
        byte[] secondaryBytes = renderAnnual(secondaryLines, "secondary", "FR", true);
        List<BulletinLineView> primaryLines = IntStream.range(0, 21)
                .mapToObj(i -> annualLine("SUBJECT" + i, "Science and Technology — Technology and Engineering", null)).toList();
        byte[] primaryBytes = renderAnnual(primaryLines, "primary", "EN", false);
        for (byte[] bytes : List.of(secondaryBytes, primaryBytes)) {
            try (PDDocument document = PDDocument.load(bytes)) {
                assertThat(document.getNumberOfPages()).isEqualTo(1);
                String text = flatText(document);
                assertThat(text).doesNotContain("...");
            }
        }
        try (PDDocument document = PDDocument.load(secondaryBytes)) {
            assertThat(flatText(document)).contains("Éducation à la citoyenneté et à la morale", "NSAH ZOUFANE",
                    "Technologies de l'information et de la communication");
        }
        try (PDDocument document = PDDocument.load(primaryBytes)) {
            assertThat(flatText(document)).contains("Science and Technology — Technology and Engineering");
        }
        writeQa("secondary-annual-fr-long-subjects.pdf", secondaryBytes);
        writeQa("primary-annual-en-long-subjects.pdf", primaryBytes);
    }

    @Test
    void secondarySequenceCardWithSeventeenSubjectsFitsOnOnePage() throws Exception {
        List<String> subjects = List.of("Physical Education", "Agriculture and Animal Husbandry",
                "Literature in English", "Computer Science", "Home Economics");
        List<BulletinLineView> lines = IntStream.range(0, 17)
                .mapToObj(i -> periodLine("SUBJECT" + i, subjects.get(i % subjects.size()), "BOUBAKARI BOUBA",
                        "S1", "Satisfactory work")).toList();
        byte[] bytes = renderCard(lines, "secondary", "EN", false, "S1", "Sequence 1", "SEQUENCE", "SEQUENCE");
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = flatText(document);
            assertThat(text).contains("Agriculture and Animal Husbandry", "Literature in English", "Satisfactory work",
                    "COMPETENCIES EVALUATED", "Class council decision", "Promoted / Good work");
            assertThat(text).doesNotContain("...");
        }
        writeQa("secondary-sequence-en-17-subjects.pdf", bytes);
    }

    @Test
    void primaryTermCardWithEighteenSubjectsFitsOnOnePage() throws Exception {
        List<BulletinLineView> lines = IntStream.range(0, 18)
                .mapToObj(i -> periodLine("SUBJECT" + i, "Mathématiques — Géométrie et espace " + i, null,
                        "S" + (i % 2 + 1), "Travail satisfaisant")).toList();
        byte[] bytes = renderCard(lines, "primary", "FR", true, "T1_RESULT", "Résultat Trimestre 1", "TERM_RESULT", "TERM");
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = flatText(document);
            assertThat(text).contains("Mathématiques — Géométrie et espace 17", "Travail satisfaisant", "Fait à");
            assertThat(text).doesNotContain("...");
        }
        writeQa("primary-term-fr-18-subjects.pdf", bytes);
    }

    @Test
    void periodCardsWithTooManySubjectsStillPaginate() throws Exception {
        List<BulletinLineView> lines = IntStream.rangeClosed(1, 40)
                .mapToObj(i -> periodLine("SUBJECT" + i, "Subject " + i, "TEACHER " + i, "S1", "Good")).toList();
        try (PDDocument document = PDDocument.load(renderCard(lines, "secondary", "EN", false,
                "S1", "Sequence 1", "SEQUENCE", "SEQUENCE"))) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
        }
    }

    private BulletinLineView periodLine(String code, String label, String teacher, String period, String remark) {
        return new BulletinLineView(code, label, 1, new BigDecimal("13.15"), new BigDecimal("13.15"),
                remark, remark, List.of(), List.of(new PeriodMarkView(period, new BigDecimal("12.7"))),
                teacher, null, null);
    }

    private static String flatText(PDDocument document) throws Exception {
        return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
    }

    private BulletinLineView annualLine(String code, String label, String teacher) {
        return new BulletinLineView(code, label, 1, new BigDecimal("15.4"), new BigDecimal("15.4"),
                "Very good", "Very good", List.of(),
                List.of(new PeriodMarkView("T1_RESULT", new BigDecimal("15.3")),
                        new PeriodMarkView("T2_RESULT", new BigDecimal("15.4")),
                        new PeriodMarkView("T3_RESULT", new BigDecimal("15.5"))),
                teacher, null, null);
    }

    private byte[] renderAnnual(List<BulletinLineView> lines, String level, String subsystem, boolean french) {
        return renderCard(lines, level, subsystem, french, "ANNUAL", "Annual", "ANNUAL_RESULT", "ANNUAL");
    }

    private byte[] renderCard(List<BulletinLineView> lines, String level, String subsystem, boolean french,
                              String code, String label, String periodType, String product) {
        UUID snapshotId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        BulletinSnapshotView view = snapshot(snapshotId, lines, code, label, periodType, product, level, subsystem);
        when(snapshots.byId(snapshotId)).thenReturn(view);
        when(photos.findByOwnerTypeAndOwnerIdAndSchoolId("student", view.studentId(), schoolId))
                .thenReturn(Optional.empty());
        TenantContext.set(schoolId);
        try {
            return new ReportCardPdfService(snapshots, photos, classes, jdbc).render(snapshotId, french);
        } finally {
            TenantContext.clear();
        }
    }

    private BulletinSnapshotView snapshot(UUID snapshotId, BulletinLineView line, String code, String label,
                                          String periodType, String product) {
        return snapshot(snapshotId, List.of(line), code, label, periodType, product, "secondary", "EN");
    }

    private BulletinSnapshotView snapshot(UUID snapshotId, List<BulletinLineView> lines, String code, String label,
                                          String periodType, String product, String level, String subsystem) {
        return new BulletinSnapshotView(
                snapshotId, UUID.randomUUID(), UUID.randomUUID(), code, label,
                UUID.randomUUID(), "MBOUENDE Jeanne", "BBC-1002", level, subsystem, "Form I",
                lines, new BigDecimal("13.5"), 3, 28, "PUBLISHED", true, List.of(),
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
