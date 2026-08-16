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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
        boolean reportFrench = secondary ? !"EN".equalsIgnoreCase(b.subsystem()) : french;
        boolean annual = annual(b);
        boolean computed = computed(b);
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
                float y = header(doc, page, b, reportFrench, image, secondary, branding, logo);
                y = secondary ? secondaryTableHeader(doc, page, y, reportFrench, annual, templateFamily)
                        : computed ? computedTableHeader(doc, page, y, reportFrench, b)
                        : tableHeader(doc, page, y, reportFrench, false, annual, templateFamily);
                for (int lineIndex = 0; lineIndex < b.lines().size(); lineIndex++) {
                    BulletinLineView line = b.lines().get(lineIndex);
                    float rowHeight = secondary ? secondaryRowHeight(line, annual)
                            : computed ? computedRowHeight(line, b) : 22;
                    if (y - rowHeight < 78) {
                        footer(doc, page, reportFrench);
                        page = new PDPage(PDRectangle.A4); doc.addPage(page);
                        y = header(doc, page, b, reportFrench, image, secondary, branding, logo);
                        y = secondary ? secondaryTableHeader(doc, page, y, reportFrench, annual, templateFamily)
                                : computed ? computedTableHeader(doc, page, y, reportFrench, b)
                                : tableHeader(doc, page, y, reportFrench, false, annual, templateFamily);
                    }
                    if (secondary) y -= secondaryRow(doc, page, y, line, reportFrench, annual);
                    else if (computed) y -= computedRow(doc, page, y, line, reportFrench, b);
                    else { row(doc, page, y, line, reportFrench, false); y -= 22; }
                }
                y -= 5;
                if (secondary) y -= secondaryTotals(doc, page, y, b, reportFrench, annual);
                if (y < 230) { footer(doc, page, reportFrench); page = new PDPage(PDRectangle.A4); doc.addPage(page); y = header(doc, page, b, reportFrench, image, secondary, branding, logo) - 10; }
                summary(doc, page, y, b, reportFrench, secondary);
                signatureBoxes(doc, page, y - 148, reportFrench, branding, stamp);
                // Keep the verification mark in the footer band so it never
                // obscures the result table, conduct block, or signatures.
                drawQr(doc, page, b, 485, 58);
                footer(doc, page, reportFrench);
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
        if (secondary) return secondaryHeader(doc, page, b, fr, branding, logo);
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
        String location = branding == null ? "Maroua" : blankJoin(
                blankJoin(branding.address(), blankJoin(branding.city(), branding.country(), ""), ""),
                "Official academic report card", "Maroua");
        text(cs, normal(), 9, 238, 740, clip(location, 72));
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

    /** Header and identity grid used by the official Francophone/Anglophone secondary forms. */
    private float secondaryHeader(PDDocument doc, PDPage page, BulletinSnapshotView b, boolean fr,
                                  BrandingRenderData branding, PDImageXObject logo) throws Exception {
        StudentRenderData student = studentData(b);
        String schoolName = branding == null || branding.schoolName() == null || branding.schoolName().isBlank()
                ? "BAYO BILINGUAL COMPLEX" : branding.schoolName();
        String schoolNameEn = branding == null || branding.schoolNameEn() == null || branding.schoolNameEn().isBlank()
                ? schoolName : branding.schoolNameEn();
        String ministry = branding == null || branding.ministryText() == null || branding.ministryText().isBlank()
                ? "MINISTERE DES ENSEIGNEMENTS SECONDAIRES" : branding.ministryText();
        String delegation = branding == null || branding.delegationText() == null || branding.delegationText().isBlank()
                ? "DELEGATION REGIONALE / DEPARTEMENTALE" : branding.delegationText();
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            text(cs, bold(), 7, LEFT, 814, "REPUBLIQUE DU CAMEROUN");
            text(cs, normal(), 6, LEFT + 18, 804, "Paix-Travail-Patrie");
            text(cs, bold(), 6, LEFT, 791, clip(ministry, 42));
            text(cs, normal(), 6, LEFT, 781, clip(delegation, 42));
            text(cs, bold(), 7, 407, 814, "REPUBLIC OF CAMEROON");
            text(cs, normal(), 6, 430, 804, "Peace-Work-Fatherland");
            text(cs, bold(), 6, 392, 791, "MINISTRY OF SECONDARY EDUCATION");
            text(cs, normal(), 6, 392, 781, "REGIONAL / DIVISIONAL DELEGATION");
            if (logo != null) cs.drawImage(logo, 274, 780, 46, 46);
            text(cs, bold(), 8, 220, 766, clip(fr ? schoolName : schoolNameEn, 34));
            if (branding != null && branding.motto() != null && !branding.motto().isBlank())
                text(cs, normal(), 6, 245, 755, clip(branding.motto(), 28));
            line(cs, LEFT, 744, RIGHT, 744, 1f);

            String title = reportTitle(b, fr);
            text(cs, bold(), 13, Math.max(LEFT, 297 - title.length() * 3.4f), 726, title);
            text(cs, bold(), 9, 225, 713, (fr ? "ANNEE SCOLAIRE : " : "SCHOOL YEAR : ") + student.schoolYear());

            float top = 700;
            float bottom = 632;
            box(cs, LEFT, bottom, RIGHT, top);
            line(cs, 360, bottom, 360, top, .45f);
            line(cs, LEFT, 683, RIGHT, 683, .35f);
            line(cs, LEFT, 666, RIGHT, 666, .35f);
            line(cs, LEFT, 649, RIGHT, 649, .35f);
            text(cs, bold(), 7, 48, 689, fr ? "NOM ET PRENOMS" : "NAME OF STUDENT");
            text(cs, bold(), 8, 142, 689, safeText(b.studentName()));
            text(cs, bold(), 7, 368, 689, fr ? "CLASSE" : "CLASS");
            text(cs, bold(), 8, 424, 689, safeText(b.className()));
            text(cs, normal(), 7, 48, 672, (fr ? "NE(E) LE / A : " : "BORN ON / AT: ") + student.birth());
            text(cs, normal(), 7, 368, 672, (fr ? "MATRICULE : " : "STUDENT ID: ") + safeText(b.matricule()));
            text(cs, normal(), 7, 48, 655, (fr ? "SEXE : " : "GENDER: ") + student.sex()
                    + "     " + (fr ? "REDOUBLANT(E) : " : "REPEATER: ") + student.repeater());
            text(cs, normal(), 7, 368, 655, (fr ? "EFFECTIF : " : "CLASS ENROLMENT: ") + b.classSize());
            text(cs, normal(), 7, 48, 638, (fr ? "PARENT / TUTEUR : " : "PARENT / GUARDIAN: ") + clip(student.parent(), 42));
            text(cs, normal(), 7, 368, 638, (fr ? "PROF. TITULAIRE : " : "CLASS MASTER: ") + clip(student.classMaster(), 25));
        }
        return 616;
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

    private float computedTableHeader(PDDocument doc, PDPage page, float y, boolean fr,
                                      BulletinSnapshotView bulletin) throws Exception {
        List<String> periods = dependencyCodes(bulletin);
        float subjectRight = 145;
        float currentRight = 145 + Math.max(42, 32 * Math.max(1, periods.size()));
        float coefficientRight = currentRight + 38;
        float weightedRight = coefficientRight + 58;
        float appreciationRight = RIGHT;
        float componentWidth = periods.isEmpty() ? 0 : (currentRight - subjectRight) / periods.size();
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(BLUE, 0.48f, 0.70f);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(1, 1, 1);
            text(cs, bold(), 7, 48, y - 10, fr ? "MATIERE" : "SUBJECT");
            for (int i = 0; i < periods.size(); i++) text(cs, bold(), 7,
                    subjectRight + i * componentWidth + 4, y - 10, clip(periods.get(i), 7));
            text(cs, bold(), 7, currentRight + 4, y - 10,
                    bulletin.product() != null && bulletin.product().equalsIgnoreCase("ANNUAL") ? (fr ? "ANNUEL" : "ANNUAL") : (fr ? "TERME" : "TERM"));
            text(cs, bold(), 7, coefficientRight + 4, y - 10, "COEF");
            text(cs, bold(), 7, weightedRight + 4, y - 10, fr ? "PONDERE" : "WEIGHTED");
            text(cs, bold(), 7, weightedRight + 62, y - 10, fr ? "APPRECIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            List<Float> boundaries = new java.util.ArrayList<>();
            boundaries.add(LEFT); boundaries.add(subjectRight);
            for (int i = 0; i < periods.size(); i++) boundaries.add(subjectRight + (i + 1) * componentWidth);
            boundaries.add(coefficientRight); boundaries.add(weightedRight); boundaries.add(appreciationRight);
            for (float x : boundaries) line(cs, x, y + 4, x, y - 18, 0.6f);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    private float computedRow(PDDocument doc, PDPage page, float y, BulletinLineView line,
                              boolean fr, BulletinSnapshotView bulletin) throws Exception {
        List<String> periods = dependencyCodes(bulletin);
        float subjectRight = 145;
        float currentRight = 145 + Math.max(42, 32 * Math.max(1, periods.size()));
        float coefficientRight = currentRight + 38;
        float weightedRight = coefficientRight + 58;
        float componentWidth = periods.isEmpty() ? 0 : (currentRight - subjectRight) / periods.size();
        float height = 24;
        String remark = line.teacherRemark() == null ? line.appreciation() : line.teacherRemark();
        if (remark != null && remark.length() > 26) height = 32;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            text(cs, bold(), 7, 48, y - 14, clip(line.subjectLabel(), 20));
            for (int i = 0; i < periods.size(); i++) {
                String periodCode = periods.get(i);
                BigDecimal mark = line.periodMarks() == null ? null : line.periodMarks().stream()
                        .filter(value -> periodCode.equals(value.periodCode())).map(PeriodMarkView::mark).findFirst().orElse(null);
                text(cs, normal(), 8, subjectRight + i * componentWidth + 4, y - 14, number(mark));
            }
            text(cs, bold(), 8, currentRight + 4, y - 14, number(line.mark()));
            text(cs, normal(), 8, coefficientRight + 4, y - 14, String.valueOf(line.coefficient()));
            text(cs, normal(), 8, weightedRight + 4, y - 14, number(line.weighted()));
            text(cs, normal(), 7, weightedRight + 62, y - 14, clip(remark, 26));
            List<Float> boundaries = new java.util.ArrayList<>();
            boundaries.add(LEFT); boundaries.add(subjectRight);
            for (int i = 0; i < periods.size(); i++) boundaries.add(subjectRight + (i + 1) * componentWidth);
            boundaries.add(coefficientRight); boundaries.add(weightedRight); boundaries.add(RIGHT);
            for (float x : boundaries) line(cs, x, y, x, y - height, 0.35f);
            line(cs, LEFT, y - height, RIGHT, y - height, 0.35f);
        }
        return height;
    }

    private float computedRowHeight(BulletinLineView line, BulletinSnapshotView bulletin) {
        String remark = line.teacherRemark() == null ? line.appreciation() : line.teacherRemark();
        return remark != null && remark.length() > 26 ? 32 : 24;
    }

    private List<String> dependencyCodes(BulletinSnapshotView bulletin) {
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        for (BulletinLineView line : bulletin.lines()) {
            if (line.periodMarks() == null) continue;
            for (PeriodMarkView mark : line.periodMarks()) if (mark.periodCode() != null && !mark.periodCode().isBlank()) codes.add(mark.periodCode());
        }
        if (!codes.isEmpty()) return List.copyOf(codes);
        if (bulletin.workflowMeta() != null && bulletin.workflowMeta().dependencies() != null)
            return bulletin.workflowMeta().dependencies().stream().map(DependencyReadinessView::code).toList();
        return List.of();
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
        float[] boundaries = annual
                ? new float[]{42, 170, 202, 234, 266, 300, 333, 375, 410, 460, 553}
                : new float[]{42, 135, 290, 320, 350, 383, 425, 460, 500, 553};
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(.82f, .82f, .82f);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(0, 0, 0);
            text(cs, bold(), 7, 48, y - 10, fr ? "MATIERE / PROF." : "SUBJECT / TEACHER");
            if (annual) {
                text(cs, bold(), 6, 179, y - 10, "T1");
                text(cs, bold(), 6, 211, y - 10, "T2");
                text(cs, bold(), 6, 243, y - 10, "T3");
                text(cs, bold(), 6, 270, y - 10, fr ? "MOY" : "AV");
                text(cs, bold(), 6, 306, y - 10, "COEF");
                text(cs, bold(), 6, 341, y - 10, fr ? "PROD" : "PRODUCT");
                text(cs, bold(), 6, 381, y - 10, fr ? "COTE" : "GRADE");
                text(cs, bold(), 6, 416, y - 10, "[MIN-MAX]");
                text(cs, bold(), 6, 466, y - 10, fr ? "APPRECIATION / VISA" : "REMARK / SIGNATURE");
            } else {
                text(cs, bold(), 6, 142, y - 10, fr ? "COMPETENCES EVALUEES" : "COMPETENCIES EVALUATED");
                text(cs, bold(), 6, 296, y - 10, "N/20");
                text(cs, bold(), 6, 326, y - 10, "M/20");
                text(cs, bold(), 6, 355, y - 10, "COEF");
                text(cs, bold(), 6, 389, y - 10, fr ? "PROD" : "PRODUCT");
                text(cs, bold(), 6, 431, y - 10, fr ? "COTE" : "GRADE");
                text(cs, bold(), 6, 466, y - 10, "[MIN-MAX]");
                text(cs, bold(), 6, 505, y - 10, fr ? "APPREC. / VISA" : "REMARK / SIGN.");
            }
            for (float x : boundaries) line(cs, x, y + 4, x, y - 18, 0.6f);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    /** Draw a secondary row with wrapped competency evidence and return its exact height. */
    private float secondaryRow(PDDocument doc, PDPage page, float y, BulletinLineView l,
                               boolean fr, boolean annual) throws Exception {
        float height = secondaryRowHeight(l, annual);
        List<String> competencies = new java.util.ArrayList<>();
        List<String> competencyMarks = new java.util.ArrayList<>();
        if (!annual && l.assessments() != null) {
            for (AssessmentEvidenceView a : l.assessments()) {
                String label = a.label() == null || a.label().isBlank() ? a.code() : a.label();
                List<String> wrapped = wrap(label, 31);
                competencies.addAll(wrapped);
                competencyMarks.add(a.mark() == null ? "-" : number(a.mark()));
                for (int i = 1; i < wrapped.size(); i++) competencyMarks.add("");
            }
        }
        if (competencies.isEmpty() && !annual) {
            competencies.add(componentText(l));
            competencyMarks.add(number(l.mark()));
        }
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
                text(cs, normal(), 8, 307, y - 12, String.valueOf(l.coefficient()));
                text(cs, normal(), 8, 341, y - 12, number(l.weighted()));
                text(cs, bold(), 7, 381, y - 12, grade(l.mark()));
                text(cs, normal(), 7, 418, y - 12, "-");
                drawWrapped(cs, remarkLines, 466, y - 12, 6, 8);
            } else {
                drawWrapped(cs, competencies, 140, y - 12, 6, 8);
                drawWrapped(cs, competencyMarks, 296, y - 12, 6, 8);
                text(cs, bold(), 8, 326, y - 14, number(l.mark()));
                text(cs, normal(), 8, 357, y - 14, String.valueOf(l.coefficient()));
                text(cs, normal(), 8, 390, y - 14, number(l.weighted()));
                text(cs, bold(), 7, 432, y - 14, grade(l.mark()));
                text(cs, normal(), 7, 467, y - 14, "-");
                drawWrapped(cs, remarkLines, 505, y - 12, 6, 8);
            }
            float[] boundaries = annual
                    ? new float[]{42, 170, 202, 234, 266, 300, 333, 375, 410, 460, 553}
                    : new float[]{42, 135, 290, 320, 350, 383, 425, 460, 500, 553};
            for (float x : boundaries) line(cs, x, y, x, y - height, 0.35f);
            line(cs, LEFT, y - height, RIGHT, y - height, 0.35f);
        }
        return height;
    }

    private float secondaryRowHeight(BulletinLineView l, boolean annual) {
        List<String> competencies = new java.util.ArrayList<>();
        if (!annual && l.assessments() != null) {
            for (AssessmentEvidenceView a : l.assessments()) {
                String label = a.label() == null || a.label().isBlank() ? a.code() : a.label();
                competencies.addAll(wrap(label, 31));
            }
        }
        if (competencies.isEmpty() && !annual) competencies.add(componentText(l));
        String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
        List<String> remarkLines = wrap(remark, 17);
        return Math.max(Math.max(22, 8 + Math.max(1, competencies.size()) * 9),
                8 + Math.max(1, remarkLines.size()) * 9);
    }

    private void drawAnnualMarks(PDPageContentStream cs, BulletinLineView l, float y) throws Exception {
        List<PeriodMarkView> marks = annualPeriodMarks(l);
        for (int index = 0; index < 3; index++) {
            BigDecimal value = index < marks.size() ? marks.get(index).mark() : null;
            text(cs, normal(), 7, 178 + index * 32, y, number(value));
        }
        text(cs, bold(), 7, 272, y, number(l.mark()));
    }

    private List<PeriodMarkView> annualPeriodMarks(BulletinLineView line) {
        if (line.periodMarks() == null) return List.of();
        List<PeriodMarkView> termMarks = line.periodMarks().stream()
                .filter(value -> {
                    String code = value.periodCode() == null ? "" : value.periodCode().toUpperCase(Locale.ROOT);
                    return code.matches("T[123](_RESULT)?") || code.contains("TRIM");
                })
                .sorted(Comparator.comparingInt(value -> periodNumber(value.periodCode())))
                .toList();
        return termMarks.isEmpty() ? line.periodMarks().stream()
                .sorted(Comparator.comparingInt(value -> periodNumber(value.periodCode())))
                .limit(3).toList() : termMarks;
    }

    private float secondaryTotals(PDDocument doc, PDPage page, float y, BulletinSnapshotView bulletin,
                                  boolean fr, boolean annual) throws Exception {
        int coefficient = bulletin.lines().stream().mapToInt(BulletinLineView::coefficient).sum();
        BigDecimal product = bulletin.lines().stream().map(BulletinLineView::weighted)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        float totalHeight = annual ? 34 : 18;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(.76f, .76f, .76f); cs.addRect(LEFT, y - 18, RIGHT - LEFT, 18); cs.fill();
            cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, y - 18, RIGHT, y);
            if (annual) {
                line(cs, 300, y - 18, 300, y, .45f); line(cs, 333, y - 18, 333, y, .45f); line(cs, 375, y - 18, 375, y, .45f);
                text(cs, bold(), 8, 176, y - 12, "TOTAL"); text(cs, bold(), 8, 309, y - 12, String.valueOf(coefficient));
                text(cs, bold(), 8, 340, y - 12, number(product));
                text(cs, bold(), 8, 405, y - 12, (fr ? "Moyenne : " : "Student average: ") + number(bulletin.average()));
                cs.setNonStrokingColor(.86f, .86f, .86f); cs.addRect(LEFT, y - 34, RIGHT - LEFT, 16); cs.fill();
                cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, y - 34, RIGHT, y - 18);
                float segment = (RIGHT - 210) / 3f;
                line(cs, 210, y - 34, 210, y - 18, .4f);
                line(cs, 210 + segment, y - 34, 210 + segment, y - 18, .4f);
                line(cs, 210 + 2 * segment, y - 34, 210 + 2 * segment, y - 18, .4f);
                text(cs, bold(), 6, 74, y - 29, fr ? "RAPPEL DES MOYENNES TRIMESTRIELLES" : "TERM AVERAGES");
                List<String> periodCodes = annualPeriodCodes(bulletin);
                for (int index = 0; index < 3; index++) {
                    BigDecimal average = index < periodCodes.size() ? weightedPeriodAverage(bulletin, periodCodes.get(index)) : null;
                    String label = fr ? (index + 1) + (index == 0 ? "er" : "e") + " Trimestre" : switch (index) { case 0 -> "1st Term"; case 1 -> "2nd Term"; default -> "3rd Term"; };
                    text(cs, bold(), 6, 218 + index * segment, y - 29, label + " : " + number(average));
                }
            } else {
                line(cs, 290, y - 18, 290, y, .45f); line(cs, 350, y - 18, 350, y, .45f);
                text(cs, bold(), 8, 150, y - 12, "TOTAL"); text(cs, bold(), 8, 309, y - 12, String.valueOf(coefficient));
                text(cs, bold(), 8, 365, y - 12, number(product));
                text(cs, bold(), 8, 440, y - 12, (fr ? "Moyenne : " : "Average: ") + number(bulletin.average()));
            }
        }
        return totalHeight;
    }

    private List<String> annualPeriodCodes(BulletinSnapshotView bulletin) {
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        for (BulletinLineView line : bulletin.lines()) if (line.periodMarks() != null) for (PeriodMarkView period : line.periodMarks()) {
            String code = period.periodCode() == null ? "" : period.periodCode().toUpperCase(Locale.ROOT);
            if (code.matches("T[123](_RESULT)?") || code.contains("TRIM")) codes.add(period.periodCode());
        }
        return codes.stream().sorted(Comparator.comparingInt(ReportCardPdfService::periodNumber)).limit(3).toList();
    }

    private BigDecimal weightedPeriodAverage(BulletinSnapshotView bulletin, String code) {
        BigDecimal total = BigDecimal.ZERO; int coefficients = 0;
        for (BulletinLineView line : bulletin.lines()) {
            BigDecimal mark = line.periodMarks() == null ? null : line.periodMarks().stream()
                    .filter(value -> code.equals(value.periodCode())).map(PeriodMarkView::mark).findFirst().orElse(null);
            if (mark != null) { total = total.add(mark.multiply(BigDecimal.valueOf(line.coefficient()))); coefficients += line.coefficient(); }
        }
        return coefficients == 0 ? null : total.divide(BigDecimal.valueOf(coefficients), 2, java.math.RoundingMode.HALF_UP);
    }

    private static int periodNumber(String code) {
        if (code == null) return 99;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([1-6])").matcher(code);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 99;
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
            float x1 = 214, x2 = 384;
            float headerBottom = y - 18, bottom = y - 126;
            cs.setNonStrokingColor(.9f, .9f, .9f); cs.addRect(LEFT, headerBottom, RIGHT - LEFT, 18); cs.fill();
            cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, bottom, RIGHT, y);
            line(cs, x1, bottom, x1, y, .45f); line(cs, x2, bottom, x2, y, .45f);
            for (int row = 0; row <= 6; row++) line(cs, LEFT, headerBottom - row * 18, RIGHT, headerBottom - row * 18, .35f);
            text(cs, bold(), 8, 105, y - 13, fr ? "Discipline" : "Discipline");
            text(cs, bold(), 8, 265, y - 13, fr ? "Travail de l'eleve" : "Student performance");
            text(cs, bold(), 8, 445, y - 13, fr ? "Profil de la classe" : "Class Profile");

            String unexcused = b.attendance() == null ? "0" : number(b.attendance().unjustifiedAbsenceHours());
            String excused = b.attendance() == null ? "0" : number(b.attendance().justifiedAbsenceHours());
            String late = b.attendance() == null ? "0" : number(BigDecimal.valueOf(b.attendance().lateMinutes()).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
            String[] disciplineLabels = fr ? new String[]{"Absences non J.","Absences J.","Retards (heures)","Avert. conduite","Blame conduite","Exclusion (jours)"}
                    : new String[]{"Absences non J.","Absences J.","Lateness (hours)","Conduct warning","Reprimand","Suspension (days)"};
            String[] disciplineValues = new String[]{unexcused,excused,late,
                    conduct != null && conduct.conductWarning() ? "X" : "-",
                    conduct != null && conduct.conductBlame() ? "X" : "-",
                    conduct == null ? "0" : String.valueOf(conduct.exclusionDays())};
            String[] workLabels = fr ? new String[]{"Total general","Total coef.","Moyenne","Cote","Decision du conseil","Distinctions"}
                    : new String[]{"Total score","Total coef.","Average","Grade","Class council decision","Awards"};
            int totalCoefficient = b.lines().stream().mapToInt(BulletinLineView::coefficient).sum();
            BigDecimal totalScore = b.lines().stream().map(BulletinLineView::weighted).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            String[] workValues = new String[]{number(totalScore),String.valueOf(totalCoefficient),number(b.average()),grade(b.average()),clip(decision,18),clip(conductText,18)};
            String[] classLabels = fr ? new String[]{"Moyenne generale","Rang","Nombre de moyennes","Taux de reussite","Minimum","Maximum"}
                    : new String[]{"Class average","Rank","Number passed","Success rate","Minimum","Maximum"};
            String[] classValues = new String[]{stats == null ? "-" : number(stats.average()),
                    (b.rank() == null ? "-" : b.rank()) + " / " + b.classSize(),
                    stats == null ? "-" : String.valueOf(stats.successCount()), stats == null ? "-" : number(stats.successRate()) + "%",
                    stats == null ? "-" : number(stats.minimum()), stats == null ? "-" : number(stats.maximum())};
            for (int row = 0; row < 6; row++) {
                float ty = headerBottom - 13 - row * 18;
                text(cs, normal(), 6, 47, ty, clip(disciplineLabels[row], 24)); text(cs, bold(), 7, 188, ty, disciplineValues[row]);
                text(cs, normal(), 6, 219, ty, clip(workLabels[row], 15)); text(cs, bold(), 6, 315, ty, clip(workValues[row], 11));
                text(cs, normal(), 6, 389, ty, clip(classLabels[row], 17)); text(cs, bold(), 6, 510, ty, clip(classValues[row], 8));
            }
        }
    }

    private void signatureBoxes(PDDocument doc, PDPage page, float top, boolean fr,
                                BrandingRenderData branding, PDImageXObject stamp) throws Exception {
        float x1 = 250, x2 = 350, x3 = 450;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            box(cs, LEFT, top - 70, RIGHT, top);
            line(cs, x1, top - 70, x1, top, .5f); line(cs, x2, top - 70, x2, top, .5f); line(cs, x3, top - 70, x3, top, .5f);
            text(cs, bold(), 7, LEFT + 6, top - 13, fr ? "APPRECIATION DU TRAVAIL DE L'ELEVE" : "REMARKS ON STUDENT PERFORMANCE");
            String headTitle = branding == null || branding.principalTitle() == null || branding.principalTitle().isBlank()
                    ? (fr ? "CHEF D'ETABLISSEMENT" : "HEAD OF SCHOOL") : branding.principalTitle();
            text(cs, normal(), 7, x1 + 7, top - 13, fr ? "Visa du parent / Tuteur" : "Parent / Guardian signature");
            text(cs, normal(), 7, x2 + 7, top - 13, fr ? "Nom et visa du professeur" : "Class master's signature");
            text(cs, normal(), 7, x3 + 7, top - 13, fr ? "Fait a MAROUA, le" : "At MAROUA, on");
            text(cs, bold(), 7, x3 + 7, top - 26, clip(headTitle, 22));
            if (stamp != null) cs.drawImage(stamp, x3 + 55, top - 64, 34, 34);
        }
    }

    private String reportTitle(BulletinSnapshotView bulletin, boolean fr) {
        if (annual(bulletin)) return fr ? "BULLETIN SCOLAIRE ANNUEL" : "ANNUAL REPORT SHEET";
        int number = periodNumber(bulletin.reportingPeriodCode());
        if ("TERM_RESULT".equalsIgnoreCase(bulletin.reportingPeriodType())
                || "TERM".equalsIgnoreCase(bulletin.product()))
            return fr ? "BULLETIN SCOLAIRE DU " + (number == 1 ? "1er" : number + "e") + " TRIMESTRE"
                    : switch (number) { case 1 -> "FIRST TERM PROGRESS RECORD"; case 2 -> "SECOND TERM PROGRESS RECORD"; default -> "THIRD TERM PROGRESS RECORD"; };
        return fr ? "BULLETIN DE NOTES DE LA SEQUENCE " + number : "SEQUENCE " + number + " PROGRESS RECORD";
    }

    private StudentRenderData studentData(BulletinSnapshotView bulletin) {
        StudentRenderData fallback = new StudentRenderData("-", "-", "-", "-", "-", "-",
                sessionCode(bulletin));
        try {
            List<StudentRenderData> rows = jdbc.query("""
                SELECT s.dob,s.birthplace,s.sex,s.repeats,
                       concat_ws(' / ',nullif(s.parent_name,''),nullif(s.parent_phone,'')) parent_contact,
                       coalesce((SELECT e.name
                                   FROM student_enrollment se
                                   JOIN class_teacher_assignment a
                                     ON a.school_id=se.school_id
                                    AND a.academic_session_id=se.academic_session_id
                                    AND a.class_id=se.school_class_id
                                    AND a.role='HOMEROOM' AND a.status='ACTIVE'
                                   JOIN employee e ON e.id=a.employee_id AND e.school_id=a.school_id
                                  WHERE se.school_id=s.school_id AND se.student_id=s.id
                                    AND se.academic_session_id=? AND se.status='ACTIVE'
                                  ORDER BY a.effective_from DESC LIMIT 1),'-') class_master,
                       coalesce((SELECT code FROM academic_session WHERE id=? AND school_id=s.school_id),'-') school_year
                  FROM student s WHERE s.id=? AND s.school_id=?
                """, (rs, n) -> new StudentRenderData(
                    rs.getObject("dob", LocalDate.class) == null ? "-"
                            : rs.getObject("dob", LocalDate.class).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    safeText(rs.getString("birthplace")), safeText(rs.getString("sex")),
                    rs.getBoolean("repeats") ? "Oui / Yes" : "Non / No",
                    safeText(rs.getString("parent_contact")), safeText(rs.getString("class_master")),
                    safeText(rs.getString("school_year"))),
                    bulletin.academicSessionId(), bulletin.academicSessionId(), bulletin.studentId(), TenantContext.get());
            return rows == null || rows.isEmpty() ? fallback : rows.getFirst();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private String sessionCode(BulletinSnapshotView bulletin) {
        try {
            List<String> values = jdbc.query("SELECT code FROM academic_session WHERE id=? AND school_id=?",
                    (rs, n) -> rs.getString(1), bulletin.academicSessionId(), TenantContext.get());
            return values == null || values.isEmpty() ? "-" : safeText(values.getFirst());
        } catch (RuntimeException ignored) {
            return "-";
        }
    }

    private record StudentRenderData(String dob, String birthplace, String sex, String repeater,
                                     String parent, String classMaster, String schoolYear) {
        String birth() { return "-".equals(dob) ? birthplace : dob + ("-".equals(birthplace) ? "" : " / " + birthplace); }
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
        if ("ANNUAL".equalsIgnoreCase(b.product())) return true;
        String code = b.reportingPeriodCode() == null ? "" : b.reportingPeriodCode().toUpperCase(Locale.ROOT);
        String label = b.reportingPeriodLabel() == null ? "" : b.reportingPeriodLabel().toUpperCase(Locale.ROOT);
        String family = b.evidence() == null || b.evidence().documentDesign() == null ? "" :
                String.valueOf(b.evidence().documentDesign().templateFamily()).toUpperCase(Locale.ROOT);
        return code.contains("ANNUAL") || label.contains("ANNUAL") || label.contains("ANNUEL") || family.endsWith("ANNUAL");
    }

    private static boolean computed(BulletinSnapshotView b) {
        return "TERM".equalsIgnoreCase(b.product()) || "ANNUAL".equalsIgnoreCase(b.product())
                || "TERM_RESULT".equalsIgnoreCase(b.reportingPeriodType())
                || "ANNUAL_RESULT".equalsIgnoreCase(b.reportingPeriodType());
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
