package com.bbc.sms.academic;

import com.bbc.sms.attendance.dto.AttendanceDtos.AttendanceSummaryView;
import com.bbc.sms.academic.dto.AcademicDtos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCardPdfServiceTest {
    @Mock BulletinSnapshotService snapshots;
    @Mock JdbcTemplate jdbc;

    @ParameterizedTest(name = "{0}/{1}/{2}")
    @CsvSource({
            "maternelle,TERM,fr,true,MISE EN PAGE MATERNELLE",
            "maternelle,ANNUAL,fr,true,MISE EN PAGE MATERNELLE",
            "primary,TERM,fr,true,MISE EN PAGE PRIMAIRE",
            "primary,ANNUAL,fr,true,MISE EN PAGE PRIMAIRE",
            "secondary,TERM,fr,true,MISE EN PAGE SECONDAIRE",
            "secondary,ANNUAL,fr,true,MISE EN PAGE SECONDAIRE",
            "maternelle,TERM,en,false,NURSERY LAYOUT",
            "maternelle,ANNUAL,en,false,NURSERY LAYOUT",
            "primary,TERM,en,false,PRIMARY LAYOUT",
            "primary,ANNUAL,en,false,PRIMARY LAYOUT",
            "secondary,TERM,en,false,SECONDARY LAYOUT",
            "secondary,ANNUAL,en,false,SECONDARY LAYOUT"
    })
    void rendersAllStandardFamiliesAndLayouts(String level, String product, String locale,
                                               boolean french, String layout) throws Exception {
        UUID snapshotId = UUID.randomUUID();
        when(snapshots.authoritativeById(snapshotId)).thenReturn(snapshot(snapshotId, level, product, locale));

        byte[] bytes = new ReportCardPdfService(snapshots, jdbc).render(snapshotId, french);

        assertThat(new String(bytes, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(595.27563f,
                    org.assertj.core.data.Offset.offset(0.01f));
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("FOTSO Cédric", layout);
            assertThat(text).contains(french ? "Moyenne" : "Student average");
            assertThat(text).contains("RÉPUBLIQUE DU CAMEROUN");
            boolean imagePresent = false;
            for (var page : document.getPages()) {
                for (var name : page.getResources().getXObjectNames()) {
                    if (page.getResources().getXObject(name) instanceof PDImageXObject) imagePresent = true;
                }
            }
            assertThat(imagePresent).isTrue();
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void rendersIdenticalBytesForTheSameFrozenSnapshot() {
        UUID snapshotId = UUID.randomUUID();
        when(snapshots.authoritativeById(snapshotId)).thenReturn(snapshot(snapshotId, "secondary", "ANNUAL", "fr"));
        ReportCardPdfService renderer = new ReportCardPdfService(snapshots, jdbc);

        byte[] first = renderer.render(snapshotId, true);
        byte[] second = renderer.render(snapshotId, true);

        assertThat(first).isEqualTo(second);
        verifyNoInteractions(jdbc);
    }

    private AuthoritativeSnapshotView snapshot(UUID id, String level, String product, String locale) {
        UUID schoolId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        DocumentDesignEvidenceView design = new DocumentDesignEvidenceView(
                UUID.randomUUID(), locale.equals("en") ? "EN_" + product : "FR_" + product,
                product, locale, 3, "template-hash-123456", null, 0, null,
                "Mme Élise MANGA", "Professeure principale", "Conseil de classe", "Chef d'établissement",
                "{\"layoutByLevel\":{\"maternelle\":\"NURSERY\",\"primary\":\"PRIMARY\",\"secondary\":\"SECONDARY\"}}");
        SnapshotSubjectResultView subject = new SnapshotSubjectResultView(
                "MATH", "Mathématiques", 4, new BigDecimal("14.125"), new BigDecimal("14.13"),
                new BigDecimal("56.50"), new BigDecimal("56.52"), "SCORED",
                "Excellent effort with a long teacher remark that must wrap without losing accents : élève sérieux et régulier.",
                "Très bon travail", List.of(new PeriodMarkView("S1", new BigDecimal("13.50")),
                new PeriodMarkView("S2", new BigDecimal("14.75"))), List.of(), 1, "SCI", "Sciences");
        SnapshotResultView result = new SnapshotResultView(new BigDecimal("14.125"), new BigDecimal("14.13"),
                1, 28, List.of(subject), List.of(new GroupStatsView("SCI", "Sciences", new BigDecimal("14.13"),
                new BigDecimal("56.52"), 4, 1)), new ClassStatsView(new BigDecimal("12.50"),
                new BigDecimal("7.25"), new BigDecimal("18.75"), 21, new BigDecimal("75.00"), 28),
                List.of(), List.of());
        SnapshotStaffView staff = new SnapshotStaffView(
                new SnapshotTeacherView(UUID.randomUUID(), "TM-1", "Mme Élise MANGA", "HOMEROOM", null, 2),
                List.of(new SnapshotTeacherView(UUID.randomUUID(), "T-1", "M. Jean N'DONGO", "SUBJECT", "MATH", 4)));
        SnapshotSchoolView school = new SnapshotSchoolView(schoolId, "BBC", "Bayo Bilingual Complex", "MINESEC",
                "123 Avenue de la République", "Maroua", "Cameroun", "+237 699 000 000", "school@example.test",
                "https://example.test", design);
        SnapshotEvidenceView evidence = new SnapshotEvidenceView(null, design, List.of(), "AcademicCalculationEngine/v2", "DEFAULT");
        return new AuthoritativeSnapshotView(1, schoolId, UUID.randomUUID(), UUID.randomUUID(),
                product.equals("ANNUAL") ? "ANNUAL_RESULT" : "T1_RESULT", product.equals("ANNUAL") ? "Année 2025-2026" : "Trimestre 1",
                product, new SnapshotStudentView(studentId, "FOTSO Cédric", "Cédric", "FOTSO", "BBC-1001",
                null, "Maroua", "M", false), new SnapshotEnrollmentView(UUID.randomUUID(), classId,
                level.equals("secondary") ? "4ème A" : "CM2 A", level, locale.equals("en") ? "EN" : "FR", 28),
                null, staff, new SnapshotPhotoView(null, null, null, null, null, "INITIALS", null), school,
                new SnapshotCurriculumView(UUID.randomUUID(), 4, "PUBLISHED", "curriculum-hash", List.of()), result,
                evidence, new AttendanceSummaryView(90, 84, 2, 3, 1, 15, new BigDecimal("2.0"),
                new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, 0),
                new ConductSummaryView(false, false, false, false, true, true, true, 0,
                        "PROMOTED", "Conseil favorable", "APPROVED"), new SnapshotTemplateView(design.templateId(),
                design.templateFamily(), product, locale, 3, design.templateHash(), design.brandingId(),
                design.brandingVersion(), design.brandingHash(), design.templateConfigJson()),
                "AcademicCalculationEngine/v2", "DEFAULT", UUID.randomUUID(), Instant.EPOCH, List.of(),
                "canonical-snapshot-hash");
    }
}
