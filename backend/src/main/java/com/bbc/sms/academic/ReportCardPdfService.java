package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.media.ProfilePhoto;
import com.bbc.sms.media.ProfilePhotoRepository;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.SchoolClassRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/** Deterministic, server-side report-card renderer for the academic snapshot. */
@Service
public class ReportCardPdfService {
    private static final float LEFT = 42;
    private static final float RIGHT = 553;
    private static final float BLUE = 0.20f;
    private static final ThreadLocal<PDFont> NORMAL_FONT = new ThreadLocal<>();
    private static final ThreadLocal<PDFont> BOLD_FONT = new ThreadLocal<>();

    private final BulletinSnapshotService snapshots;
    private final ProfilePhotoRepository photos;
    private final SchoolClassRepository classes;
    private final JdbcTemplate jdbc;

    public ReportCardPdfService(BulletinSnapshotService snapshots, ProfilePhotoRepository photos,
                                SchoolClassRepository classes, JdbcTemplate jdbc) {
        this.snapshots = snapshots; this.photos = photos; this.classes = classes; this.jdbc = jdbc;
    }

    public byte[] render(java.util.UUID snapshotId, boolean french) {
        BulletinSnapshotView b = snapshots.byId(snapshotId);
        byte[] photoBytes = snapshotPhoto(b);
        BrandingRenderData branding = branding(b);
        boolean secondary = isSecondary(b);
        boolean annual = annual(b);
        String templateFamily = b.evidence() == null || b.evidence().documentDesign() == null
                ? null : b.evidence().documentDesign().templateFamily();
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            NORMAL_FONT.set(loadFont(doc, "/usr/share/fonts/dejavu/DejaVuSans.ttf"));
            BOLD_FONT.set(loadFont(doc, "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"));
            try {
                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                PDImageXObject image = imageOrNull(doc, photoBytes, "student-photo");
                PDImageXObject logo = imageOrNull(doc, branding == null ? null : branding.logoBytes(), "school-logo");
                PDImageXObject stamp = imageOrNull(doc, branding == null ? null : branding.stampBytes(), "school-stamp");
                float y = header(doc, page, b, french, image, secondary, branding, logo);
                y = tableHeader(doc, page, y, french, secondary, annual, templateFamily);
                for (BulletinLineView line : b.lines()) {
                    float rowHeight = secondary ? secondaryRowHeight(line, annual) : 22;
                    if (y - rowHeight < 78) {
                        footer(doc, page, french);
                        page = new PDPage(PDRectangle.A4); doc.addPage(page);
                        y = header(doc, page, b, french, image, secondary, branding, logo);
                        y = tableHeader(doc, page, y, french, secondary, annual, templateFamily);
                    }
                    if (secondary) y -= secondaryRow(doc, page, y, line, french, annual);
                    else { row(doc, page, y, line, french, false); y -= 22; }
                }
                y -= 5;
                if (y < 230) { footer(doc, page, french); page = new PDPage(PDRectangle.A4); doc.addPage(page); y = header(doc, page, b, french, image, secondary, branding, logo) - 10; }
                summary(doc, page, y, b, french, secondary);
                signatureBoxes(doc, page, y - 148, french, branding, stamp);
                // Keep the verification mark in the footer band so it never
                // obscures the result table, conduct block, or signatures.
                drawQr(doc, page, b, 485, 58);
                footer(doc, page, french);
                doc.save(out);
                return out.toByteArray();
            } finally {
                NORMAL_FONT.remove();
                BOLD_FONT.remove();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Échec de génération du bulletin PDF", ex);
        }
    }

    private float header(PDDocument doc, PDPage page, BulletinSnapshotView b, boolean fr, PDImageXObject photo,
                         boolean secondary, BrandingRenderData branding, PDImageXObject logo) throws Exception {
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        line(cs, LEFT, 775, RIGHT, 775, 1.2f);
        text(cs, bold(), 10, LEFT, 805, "REPUBLIC OF CAMEROON");
        text(cs, normal(), 8, LEFT, 793, "Peace-Work-Fatherland");
        text(cs, bold(), 10, 310, 805, "RÉPUBLIQUE DU CAMEROUN");
        text(cs, normal(), 8, 310, 793, "Paix-Travail-Patrie");
        if (logo != null) cs.drawImage(logo, 48, 735, 38, 38);
        String schoolName = branding == null || branding.schoolName() == null || branding.schoolName().isBlank()
                ? "BAYO BILINGUAL COMPLEX" : (fr || branding.schoolNameEn() == null || branding.schoolNameEn().isBlank()
                ? branding.schoolName() : branding.schoolNameEn());
        text(cs, bold(), 15, 205, 754, clip(schoolName, 34));
        String location = branding == null ? "Maroua" : blankJoin(branding.city(), branding.country(), "Maroua");
        text(cs, normal(), 9, 238, 740, clip(location + " · Official academic report card", 50));
        if (branding != null && branding.ministryText() != null && !branding.ministryText().isBlank()) {
            text(cs, normal(), 7, LEFT, 719, clip(branding.ministryText(), 72));
        }
        line(cs, LEFT, 730, RIGHT, 730, 0.8f);
        text(cs, bold(), 13, 205, 707, (fr ? "BULLETIN SCOLAIRE" : "SCHOOL REPORT CARD") + " · " + safeText(b.reportingPeriodLabel()));
        text(cs, bold(), 9, LEFT, 683, fr ? "INFORMATIONS DE L'ÉLÈVE" : "STUDENT INFORMATION");
        box(cs, LEFT, 610, RIGHT, 678);
        text(cs, bold(), 10, LEFT + 10, 660, safeText(b.studentName()));
        text(cs, normal(), 9, LEFT + 10, 644, (fr ? "Matricule : " : "Student ID: ") + safeText(b.matricule()));
        text(cs, normal(), 9, LEFT + 10, 628, (fr ? "Classe : " : "Class: ") + safeText(b.className()));
        text(cs, normal(), 9, 285, 644, (fr ? "Photo et résultat officiel" : "Photo and official result"));
        text(cs, normal(), 9, 285, 628, (fr ? "État : " : "State: ") + safeText(b.state()));
        if (photo != null) cs.drawImage(photo, 472, 620, 62, 62);
        else { box(cs, 472, 620, 534, 682); text(cs, bold(), 9, 483, 648, initials(b.studentName())); }
        text(cs, bold(), 8, 285, 612, secondary ? "SECONDARY MODEL: EVALUATIONS / TERMS" : "PRIMARY MODEL: SEQUENCES / TERM COMPONENTS");
        cs.close();
        return 592;
    }

    private float tableHeader(PDDocument doc, PDPage page, float y, boolean fr, boolean secondary,
                              boolean annual, String templateFamily) throws Exception {
        if (secondary) return secondaryTableHeader(doc, page, y, fr, annual, templateFamily);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(BLUE, 0.48f, 0.70f);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22);
            cs.fill();
            cs.setNonStrokingColor(1, 1, 1);
            text(cs, bold(), 8, 48, y - 10, fr ? "MATIERE" : "SUBJECT");
            text(cs, bold(), 8, 198, y - 10, fr ? "NOTE" : "MARK");
            text(cs, bold(), 8, 245, y - 10, "COEF");
            text(cs, bold(), 8, 292, y - 10, "TOTAL");
            text(cs, bold(), 7, 337, y - 10, fr ? "COMPOSANTES" : "COMPONENTS");
            text(cs, bold(), 8, 438, y - 10, fr ? "APPRECIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            for (float x : new float[]{42, 190, 235, 280, 330, 430, 553}) {
                line(cs, x, y + 4, x, y - 18, 0.6f);
            }
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    private float tableHeaderLegacy(PDDocument doc, PDPage page, float y, boolean fr) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(BLUE, 0.48f, 0.70f); cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(1, 1, 1); text(cs, bold(), 8, 48, y - 10, fr ? "MATIÈRE" : "SUBJECT"); text(cs, bold(), 8, 245, y - 10, fr ? "NOTE" : "MARK"); text(cs, bold(), 8, 295, y - 10, "COEF"); text(cs, bold(), 8, 348, y - 10, "TOTAL"); text(cs, bold(), 8, 414, y - 10, fr ? "APPRÉCIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            for (float x : new float[]{42, 235, 285, 335, 405, 553}) line(cs, x, y + 4, x, y - 18, 0.6f);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    private float secondaryTableHeader(PDDocument doc, PDPage page, float y, boolean fr,
                                       boolean annual, String templateFamily) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(BLUE, 0.34f, 0.57f);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(1, 1, 1);
            text(cs, bold(), 7, 48, y - 10, fr ? "MATIERE / PROF." : "SUBJECT / TEACHER");
            if (annual) {
                text(cs, bold(), 7, 146, y - 10, "T1");
                text(cs, bold(), 7, 178, y - 10, "T2");
                text(cs, bold(), 7, 210, y - 10, "T3");
                text(cs, bold(), 7, 247, y - 10, fr ? "MOY" : "AV/20");
            } else {
                text(cs, bold(), 7, 142, y - 10, fr ? "COMPETENCES EVALUEES" : "COMPETENCIES EVALUATED");
                text(cs, bold(), 7, 247, y - 10, fr ? "M/20" : "MK/20");
            }
            text(cs, bold(), 7, 292, y - 10, "COEF");
            text(cs, bold(), 7, 337, y - 10, fr ? "PROD" : "PRODUCT");
            text(cs, bold(), 7, 392, y - 10, fr ? "COTE" : "GRADE");
            text(cs, bold(), 7, 450, y - 10, fr ? "APPRECIATION" : "REMARKS");
            cs.setNonStrokingColor(0, 0, 0);
            for (float x : new float[]{42, 142, 247, 292, 337, 392, 450, 553}) line(cs, x, y + 4, x, y - 18, 0.6f);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    /** Draw a secondary row with wrapped competency evidence and return its exact height. */
    private float secondaryRow(PDDocument doc, PDPage page, float y, BulletinLineView l,
                               boolean fr, boolean annual) throws Exception {
        float height = secondaryRowHeight(l, annual);
        List<String> competencies = new java.util.ArrayList<>();
        if (!annual && l.assessments() != null) {
            for (AssessmentEvidenceView a : l.assessments()) {
                String label = a.label() == null || a.label().isBlank() ? a.code() : a.label();
                String mark = a.mark() == null ? "" : "  " + number(a.mark());
                competencies.addAll(wrap(label + mark, 17));
            }
        }
        if (competencies.isEmpty() && !annual) competencies.add(componentText(l));
        int lines = Math.max(1, competencies.size());
        height = Math.max(height, Math.max(22, 8 + lines * 9));
        String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
        List<String> remarkLines = wrap(remark, 17);
        height = Math.max(height, 8 + Math.max(1, remarkLines.size()) * 9);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            text(cs, bold(), 7, 48, y - 12, clip(l.subjectLabel(), 16));
            if (l.teacherName() != null && !l.teacherName().isBlank()) text(cs, normal(), 6, 48, y - 20, clip(l.teacherName(), 17));
            // Annual rows have dedicated T1/T2/T3 cells.  Do not draw the
            // textual period labels into that same band or they overlap the
            // numeric marks and make the right-most columns unreadable.
            if (annual) {
                drawAnnualMarks(cs, l, y - 12);
            } else {
                drawWrapped(cs, competencies, 150, y - 12, 7, 9);
                text(cs, normal(), 9, 256, y - 14, number(l.mark()));
            }
            text(cs, normal(), 9, 303, y - 14, String.valueOf(l.coefficient()));
            text(cs, normal(), 9, 350, y - 14, number(l.weighted()));
            text(cs, bold(), 8, 398, y - 14, grade(l.mark()));
            drawWrapped(cs, remarkLines, 456, y - 12, 7, 9);
            for (float x : new float[]{42, 142, 247, 292, 337, 392, 450, 553}) line(cs, x, y, x, y - height, 0.35f);
            line(cs, LEFT, y - height, RIGHT, y - height, 0.35f);
        }
        return height;
    }

    private float secondaryRowHeight(BulletinLineView l, boolean annual) {
        List<String> competencies = new java.util.ArrayList<>();
        if (!annual && l.assessments() != null) {
            for (AssessmentEvidenceView a : l.assessments()) {
                String label = a.label() == null || a.label().isBlank() ? a.code() : a.label();
                String mark = a.mark() == null ? "" : "  " + number(a.mark());
                competencies.addAll(wrap(label + mark, 17));
            }
        }
        if (competencies.isEmpty() && !annual) competencies.add(componentText(l));
        String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
        List<String> remarkLines = wrap(remark, 17);
        return Math.max(Math.max(22, 8 + Math.max(1, competencies.size()) * 9),
                8 + Math.max(1, remarkLines.size()) * 9);
    }

    private void drawAnnualMarks(PDPageContentStream cs, BulletinLineView l, float y) throws Exception {
        int index = 0;
        if (l.periodMarks() != null) {
            for (PeriodMarkView mark : l.periodMarks()) {
                if (index >= 3) break;
                text(cs, normal(), 8, 150 + index * 32, y, number(mark.mark()));
                index++;
            }
        }
        text(cs, bold(), 8, 247, y, number(l.mark()));
    }

    private void row(PDDocument doc, PDPage page, float y, BulletinLineView l, boolean fr, boolean secondary) throws Exception {
        if (secondary) { secondaryRow(doc, page, y, l, fr, false); return; }
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            String subject = l.subjectGroupLabel() == null || l.subjectGroupLabel().isBlank()
                    ? l.subjectLabel() : l.subjectGroupLabel() + " / " + l.subjectLabel();
            text(cs, bold(), 8, 48, y - 14, clip(subject, 24));
            text(cs, normal(), 9, 198, y - 14, number(l.mark()));
            text(cs, normal(), 9, 247, y - 14, String.valueOf(l.coefficient()));
            text(cs, normal(), 9, 292, y - 14, number(l.weighted()));
            text(cs, normal(), 7, 337, y - 14, clip(componentText(l), 22));
            String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
            if (l.teacherName() != null && !l.teacherName().isBlank()) remark = "Prof. " + l.teacherName() + " · " + remark;
            text(cs, normal(), 7, 438, y - 14, clip(remark, 25));
            for (float x : new float[]{42, 190, 235, 280, 330, 430, 553}) {
                line(cs, x, y, x, y - 22, 0.35f);
            }
            line(cs, LEFT, y - 22, RIGHT, y - 22, 0.35f);
        }
    }

    private void rowLegacy(PDDocument doc, PDPage page, float y, BulletinLineView l, boolean fr) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            String subject = l.subjectGroupLabel() == null || l.subjectGroupLabel().isBlank()
                    ? l.subjectLabel() : l.subjectGroupLabel() + " / " + l.subjectLabel();
            text(cs, bold(), 8, 48, y - 14, clip(subject, 31));
            text(cs, normal(), 9, 245, y - 14, number(l.mark())); text(cs, normal(), 9, 298, y - 14, String.valueOf(l.coefficient())); text(cs, normal(), 9, 348, y - 14, number(l.weighted()));
            String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
            if (l.teacherName() != null && !l.teacherName().isBlank()) remark = "Prof. " + l.teacherName() + " · " + remark;
            text(cs, normal(), 8, 414, y - 14, clip(remark, 30));
            for (float x : new float[]{42, 235, 285, 335, 405, 553}) line(cs, x, y, x, y - 22, 0.35f);
            line(cs, LEFT, y - 22, RIGHT, y - 22, 0.35f);
        }
    }

    private String componentText(BulletinLineView line) {
        List<String> values = new java.util.ArrayList<>();
        if (line.assessments() != null) {
            for (AssessmentEvidenceView assessment : line.assessments()) {
                if (assessment.mark() != null) values.add(compactAssessmentCode(assessment.code()) + "=" + number(assessment.mark()));
            }
        }
        if (values.isEmpty() && line.periodMarks() != null) {
            for (PeriodMarkView periodMark : line.periodMarks()) {
                if (periodMark.mark() != null) values.add(safeText(periodMark.periodCode().replace("_RESULT", "")) + "=" + number(periodMark.mark()));
            }
        }
        values.sort(java.util.Comparator.comparingInt(this::componentRank));
        return values.isEmpty() ? "-" : String.join("/", values);
    }

    private int componentRank(String value) {
        if (value.startsWith("S")) return 0;
        if (value.startsWith("C")) return 1;
        if (value.startsWith("X")) return 2;
        return 3;
    }

    private String compactAssessmentCode(String code) {
        String normalized = safeText(code);
        java.util.regex.Matcher sequence = java.util.regex.Pattern.compile("^SEQ([0-9]+).*", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (sequence.matches()) return "S" + sequence.group(1) + "E";
        java.util.regex.Matcher scoped = java.util.regex.Pattern.compile("^SEC_S([0-9]+)_[^_]+_(CTRL|EXAM)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (scoped.matches()) return "EXAM".equalsIgnoreCase(scoped.group(2)) ? "X" : "C";
        if (normalized.endsWith("_RESULT")) return normalized.substring(0, normalized.length() - "_RESULT".length());
        return clip(normalized, 8);
    }

    private void summary(PDDocument doc, PDPage page, float y, BulletinSnapshotView b, boolean fr, boolean secondary) throws Exception {
        ClassStatsView stats = b.classStats();
        String groupText = b.groupStats() == null || b.groupStats().isEmpty()
                ? "-"
                : b.groupStats().stream()
                .map(g -> safeText((g.label() == null || g.label().isBlank()) ? g.code() : g.label()) + " " + number(g.average()))
                .reduce((first, next) -> first + " / " + next).orElse("-");
        ConductSummaryView conduct = b.conduct();
        String conductText = conduct == null ? "-" : (conduct.honorRoll() ? "Honor roll " : "")
                + (conduct.encouragement() ? "Encouragement " : "")
                + (conduct.congratulations() ? "Congratulations " : "")
                + (conduct.workWarning() ? "Work warning " : "")
                + (conduct.workBlame() ? "Work blame " : "")
                + (conduct.conductWarning() ? "Conduct warning " : "")
                + (conduct.conductBlame() ? "Conduct blame" : "");
        if (conductText.isBlank()) conductText = fr ? "Aucune distinction" : "No distinction";
        String decision = conduct == null || conduct.decisionCode() == null ? "-"
                : conduct.decisionCode() + (conduct.councilObservation() == null ? "" : " / " + conduct.councilObservation());
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            box(cs, LEFT, y - 132, RIGHT, y);
            text(cs, bold(), 9, 52, y - 16, fr ? "RESULTAT DE L'ELEVE" : "STUDENT RESULT");
            text(cs, normal(), 9, 52, y - 34, (fr ? "Moyenne : " : "Average: ") + number(b.average()) + " / 20");
            text(cs, normal(), 9, 52, y - 51, (fr ? "Rang : " : "Rank: ") + (b.rank() == null ? "-" : b.rank()) + " / " + b.classSize());
            text(cs, normal(), 9, 285, y - 34, (fr ? "Moyenne classe : " : "Class average: ") + (stats == null ? "-" : number(stats.average())));
            text(cs, normal(), 9, 285, y - 51, (fr ? "Min / Max : " : "Min / Max: ") + (stats == null ? "-" : number(stats.minimum()) + " / " + number(stats.maximum())));
            text(cs, normal(), 9, 430, y - 34, (fr ? "Reussite : " : "Pass rate: ") + (stats == null ? "-" : number(stats.successRate()) + "%"));
            text(cs, normal(), 7, 430, y - 51, fr ? "Presence :" : "Attendance:");
            text(cs, normal(), 7, 430, y - 63, b.attendance() == null ? "-" : b.attendance().presentCount() + " P / " + b.attendance().absentCount() + " A / " + number(b.attendance().unjustifiedAbsenceHours()) + "h NJ");
            text(cs, normal(), 8, 52, y - 69, (fr ? "Groupes : " : "Groups: ") + clip(groupText, 76));
            text(cs, normal(), 8, 52, y - 86, (fr ? "Conduite et distinctions : " : "Conduct and awards: ") + clip(conductText, 88));
            text(cs, normal(), 8, 52, y - 103, (fr ? "Decision du conseil : " : "Council decision: ") + clip(decision, 88));
            text(cs, normal(), 8, 52, y - 120, fr ? "Bulletin genere depuis un snapshot academique immuable." : "Generated from an immutable academic snapshot.");
        }
    }

    private void signatureBoxes(PDDocument doc, PDPage page, float top, boolean fr,
                                BrandingRenderData branding, PDImageXObject stamp) throws Exception {
        float first = LEFT;
        float width = (RIGHT - LEFT) / 3f;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            box(cs, first, top - 58, first + width, top);
            box(cs, first + width, top - 58, first + (2 * width), top);
            box(cs, first + (2 * width), top - 58, RIGHT, top);
            text(cs, bold(), 7, first + 8, top - 15, fr ? "VISA DU PARENT" : "PARENT SIGNATURE");
            String headTitle = branding == null || branding.principalTitle() == null || branding.principalTitle().isBlank()
                    ? (fr ? "CHEF D'ETABLISSEMENT" : "HEAD OF SCHOOL") : branding.principalTitle();
            String councilTitle = branding == null || branding.councilTitle() == null || branding.councilTitle().isBlank()
                    ? (fr ? "CONSEIL DE CLASSE" : "CLASS COUNCIL") : branding.councilTitle();
            text(cs, bold(), 7, first + width + 8, top - 15, councilTitle);
            text(cs, bold(), 7, first + (2 * width) + 8, top - 15, headTitle);
            text(cs, normal(), 7, first + 8, top - 45, fr ? "Signature / date" : "Signature / date");
            text(cs, normal(), 7, first + width + 8, top - 45, branding == null || branding.classMasterTitle() == null
                    ? (fr ? "Avis et visa" : "Decision / signature") : clip(branding.classMasterTitle(), 28));
            text(cs, normal(), 7, first + (2 * width) + 8, top - 45, branding == null || branding.principalName() == null
                    ? (fr ? "Cachet" : "Seal") : clip(branding.principalName(), 28));
            if (stamp != null) cs.drawImage(stamp, first + (2 * width) + 100, top - 55, 30, 30);
        }
    }

    private record BrandingRenderData(String schoolName, String schoolNameEn, String motto,
                                      String ministryText, String delegationText, String city,
                                      String country, String address, String phone,
                                      byte[] logoBytes, byte[] stampBytes, String principalName,
                                      String principalTitle, String classMasterTitle, String councilTitle) {}

    private byte[] snapshotPhoto(BulletinSnapshotView bulletin) {
        ProfileAssetEvidenceView asset = bulletin.evidence() == null ? null : bulletin.evidence().profilePhoto();
        if (asset != null && asset.assetVersionId() != null) {
            List<byte[]> bytes = jdbc.query("SELECT bytes FROM profile_photo_version WHERE id=? AND school_id=?",
                    (rs, n) -> rs.getBytes(1), asset.assetVersionId(), TenantContext.get());
            if (!bytes.isEmpty()) return bytes.get(0);
        }
        return photos.findByOwnerTypeAndOwnerIdAndSchoolId("student", bulletin.studentId(), TenantContext.get())
                .map(ProfilePhoto::getBytes).orElse(null);
    }

    private BrandingRenderData branding(BulletinSnapshotView bulletin) {
        DocumentDesignEvidenceView design = bulletin.evidence() == null ? null : bulletin.evidence().documentDesign();
        if (design == null || design.brandingId() == null) return null;
        return jdbc.query("""
                SELECT school_name,school_name_en,motto,ministry_text,delegation_text,city,country,address,phone,
                       logo_bytes,stamp_bytes,principal_name,principal_title,class_master_title,council_title
                  FROM document_branding_version
                 WHERE id=? AND school_id=?
                """, rs -> rs.next() ? new BrandingRenderData(rs.getString("school_name"), rs.getString("school_name_en"),
                        rs.getString("motto"), rs.getString("ministry_text"), rs.getString("delegation_text"),
                        rs.getString("city"), rs.getString("country"), rs.getString("address"), rs.getString("phone"),
                        rs.getBytes("logo_bytes"), rs.getBytes("stamp_bytes"), rs.getString("principal_name"),
                        rs.getString("principal_title"), rs.getString("class_master_title"), rs.getString("council_title")) : null,
                design.brandingId(), TenantContext.get());
    }

    private static PDImageXObject imageOrNull(PDDocument doc, byte[] bytes, String name) {
        if (bytes == null || bytes.length == 0) return null;
        try { return PDImageXObject.createFromByteArray(doc, bytes, name); }
        catch (Exception ignored) { return null; }
    }

    private static boolean annual(BulletinSnapshotView b) {
        String code = b.reportingPeriodCode() == null ? "" : b.reportingPeriodCode().toUpperCase(Locale.ROOT);
        String label = b.reportingPeriodLabel() == null ? "" : b.reportingPeriodLabel().toUpperCase(Locale.ROOT);
        String family = b.evidence() == null || b.evidence().documentDesign() == null ? "" :
                String.valueOf(b.evidence().documentDesign().templateFamily()).toUpperCase(Locale.ROOT);
        return code.contains("ANNUAL") || label.contains("ANNUAL") || label.contains("ANNUEL") || family.endsWith("ANNUAL");
    }

    private static String grade(java.math.BigDecimal mark) {
        if (mark == null) return "-";
        if (mark.compareTo(java.math.BigDecimal.valueOf(18)) >= 0) return "A+";
        if (mark.compareTo(java.math.BigDecimal.valueOf(16)) >= 0) return "A";
        if (mark.compareTo(java.math.BigDecimal.valueOf(14)) >= 0) return "B+";
        if (mark.compareTo(java.math.BigDecimal.TEN) >= 0) return "B";
        if (mark.compareTo(java.math.BigDecimal.valueOf(8)) >= 0) return "C";
        return "D";
    }

    private static List<String> wrap(String value, int width) {
        String clean = safeText(value);
        if (clean.isBlank()) return List.of("");
        List<String> out = new java.util.ArrayList<>();
        for (String paragraph : clean.split("\\R", -1)) {
            String remaining = paragraph.trim();
            if (remaining.isEmpty()) { out.add(""); continue; }
            while (remaining.length() > width) {
                int cut = remaining.lastIndexOf(' ', width);
                if (cut < 1) cut = width;
                out.add(remaining.substring(0, cut).trim());
                remaining = remaining.substring(cut).trim();
            }
            out.add(remaining);
        }
        return out;
    }

    private static void drawWrapped(PDPageContentStream cs, List<String> lines, float x, float y,
                                    float size, float leading) throws Exception {
        float at = y;
        for (String line : lines) { text(cs, normal(), size, x, at, line); at -= leading; }
    }

    /** QR payload is deliberately limited to an opaque snapshot checksum/id. */
    private static void drawQr(PDDocument doc, PDPage page, BulletinSnapshotView b, float x, float y) {
        String hash = b.snapshotHash() == null ? "" : b.snapshotHash();
        if (b.id() == null || hash.isBlank()) return;
        try {
            String payload = "/api/public/report-card-verification/" + b.id() + "?checksum=" + hash;
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 96, 96);
            BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
            PDImageXObject image = LosslessFactory.createFromImage(doc, qr);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.drawImage(image, x, y, 58, 58);
            }
        } catch (Exception ignored) { /* QR must never make a bulletin unprintable. */ }
    }

    private static String blankJoin(String first, String second, String fallback) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        String joined = left.isBlank() ? right : right.isBlank() ? left : left + ", " + right;
        return joined.isBlank() ? fallback : joined;
    }

    private boolean isSecondary(BulletinSnapshotView b) {
        if (b.educationalLevel() != null && !b.educationalLevel().isBlank()) {
            return "secondary".equalsIgnoreCase(b.educationalLevel());
        }
        return classes.findBySchoolIdAndName(TenantContext.get(), b.className())
                .map(c -> "secondary".equalsIgnoreCase(c.getLevel())).orElse(false);
    }

    private void footer(PDDocument doc, PDPage page, boolean fr) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            line(cs, LEFT, 50, RIGHT, 50, 0.6f); text(cs, normal(), 7, LEFT, 37, fr ? "Document généré depuis un snapshot académique immuable." : "Generated from an immutable academic snapshot."); text(cs, normal(), 7, 390, 37, fr ? "Bayo Bilingual Complex · à conserver sans rature" : "Bayo Bilingual Complex · retain without alteration");
        }
    }

    private static void text(PDPageContentStream cs, org.apache.pdfbox.pdmodel.font.PDFont font, float size, float x, float y, String value) throws Exception { cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(safeText(value)); cs.endText(); }
    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2, float w) throws Exception { cs.setStrokingColor(0.15f, 0.20f, 0.25f); cs.setLineWidth(w); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke(); }
    private static void box(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception { line(cs, x1, y1, x2, y1, 0.6f); line(cs, x1, y2, x2, y2, 0.6f); line(cs, x1, y1, x1, y2, 0.6f); line(cs, x2, y1, x2, y2, 0.6f); }
    private static PDFont loadFont(PDDocument doc, String path) {
        try {
            File file = new File(path);
            if (file.isFile()) return PDType0Font.load(doc, file);
        } catch (Exception ignored) { }
        return path.toLowerCase(Locale.ROOT).contains("bold") ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
    private static PDFont bold() { return BOLD_FONT.get() == null ? PDType1Font.HELVETICA_BOLD : BOLD_FONT.get(); }
    private static PDFont normal() { return NORMAL_FONT.get() == null ? PDType1Font.HELVETICA : NORMAL_FONT.get(); }
    private static String number(java.math.BigDecimal n) { return n == null ? "—" : n.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static String clip(String v, int n) { String s = safeText(v); return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)) + "."; }
    private static String initials(String v) { String[] p = safeText(v).split("\\s+"); return p.length < 2 ? clip(v, 3).toUpperCase(Locale.ROOT) : (p[0].substring(0, 1) + p[1].substring(0, 1)).toUpperCase(Locale.ROOT); }
    private static String safeText(String v) {
        if (v == null) return "";
        String normalized = v
                // Repair legacy UTF-8/Windows-1252 round-trip data at the PDF boundary.
                .replace("\u00c3\u2030", "É")
                .replace("\u00c3\u00a9", "é")
                .replace("\u00c3\u00a8", "è")
                .replace("\u00c3\u00aa", "ê")
                .replace("\u00c3\u00a0", "à")
                .replace("\u00c3\u00a2", "â")
                .replace("\u00c3\u00a7", "ç")
                .replace("\u00c3\u00b4", "ô")
                .replace("\u00c3\u00bb", "û")
                .replace("\u00c3\u00af", "ï")
                .replace("\u00c2\u00b7", "·")
                .replace("\u00c2\u00a0", " ")
                .replace("\u00e2\u0080\u0099", "'")
                .replace("\u00e2\u0080\u0093", "-")
                .replace("\u00e2\u0080\u0094", "-")
                .replace("\u00e2\u0080\u00a6", ".")
                .replace("\u00e2\u20ac\u2122", "'")
                .replace("\u00e2\u20ac\u201c", "-")
                .replace("\u00e2\u20ac\u201d", "-")
                .replace("\u00e2\u20ac\u00a6", ".")
                .replace('\u00a0', ' ');
        normalized = normalized
                .replace("\u00c3\u2030", "\u00c9")
                .replace("\u00c3\u00a9", "\u00e9")
                .replace("\u00c3\u00a8", "\u00e8")
                .replace("\u00c3\u00aa", "\u00ea")
                .replace("\u00c3\u00a0", "\u00e0")
                .replace("\u00c3\u00a2", "\u00e2")
                .replace("\u00c3\u00a7", "\u00e7")
                .replace("\u00c3\u00b4", "\u00f4")
                .replace("\u00c3\u00bb", "\u00fb")
                .replace("\u00c3\u00af", "\u00ef")
                .replace("\u00c2\u00b7", "\u00b7")
                .replace("\u00c2\u00a0", " ");
        // Older labels can have been persisted after two or three encoding
        // round-trips. Decode one layer at a time until the marker disappears.
        for (int pass = 0; pass < 4 && looksLikeMojibake(normalized); pass++) {
            String repaired;
            try {
                repaired = new String(normalized.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                break;
            }
            if (repaired.equals(normalized) || repaired.indexOf('\uFFFD') >= 0) break;
            normalized = repaired;
        }
        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().filter(cp -> !Character.isISOControl(cp) && cp != 0xfffd)
                .forEach(out::appendCodePoint);
        return out.toString().replace("NaN", "-").replace("nan", "-");
    }
    private static boolean looksLikeMojibake(String value) {
        return value.indexOf('\u00c3') >= 0 || value.indexOf('\u00c2') >= 0
                || value.indexOf('\u00e2') >= 0 || value.indexOf('\uFFFD') >= 0
                || value.codePoints().anyMatch(cp -> cp >= 0x80 && cp <= 0x9f);
    }
    private static String safe(String v) {
        if (v == null) return "";
        String normalized = v.replace('’','\'').replace('–','-').replace('—','-').replace('…','.')
                .replace('\u00a0', ' ');
        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().filter(cp -> !Character.isISOControl(cp) && cp != 0xfffd)
                .forEach(out::appendCodePoint);
        return out.toString();
    }
}
