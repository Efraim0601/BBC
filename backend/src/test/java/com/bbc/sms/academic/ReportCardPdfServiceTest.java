package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.media.ProfilePhotoRepository;
import com.bbc.sms.platform.tenant.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.bbc.sms.timetable.SchoolClassRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
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
        }
        verifyNoInteractions(photos);
    }
}
