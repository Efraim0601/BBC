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
import java.io.InputStream;
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
    // One palette for every card: navy table headers, pale-blue totals/panel
    // headers, hairline row separators.
    private static final float[] NAVY = {0.114f, 0.208f, 0.341f};
    private static final float[] TINT = {0.933f, 0.949f, 0.969f};
    private static final float[] ZEBRA = {0.969f, 0.976f, 0.984f};
    private static final float[] HAIRLINE = {0.80f, 0.84f, 0.89f};
    private static final float[] GOLD = {0.72f, 0.54f, 0.17f};
    // Vertical budget (pt) of a single-page annual card, measured from the page bottom.
    private static final float FOOTER_LINE = 52;
    private static final float SIGN_BOTTOM = 60;
    private static final float SIGN_MIN = 56;
    private static final float SIGN_MAX = 92;
    private static final float SUMMARY_HEIGHT = 100;
    private static final float BLOCK_GAP = 8;
    private static final float TABLE_TOP = 598;
    private static final float SHEET_ROW_MAX = 22;
    private static final float SHEET_ROW_MIN = 12.5f;
    /** Line pitch of wrapped cell text, as a multiple of the font size. */
    private static final float LEADING = 1.12f;
    /** Minimum vertical padding kept inside a table cell around its text. */
    private static final float CELL_PAD = 3;
    private static final float[] MUTED = {0.36f, 0.40f, 0.46f};
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
                // Legacy snapshots may pre-date document branding. Keep their report
                // cards branded by falling back to the packaged BBC logo.
                PDImageXObject logo = imageOrNull(doc, branding == null ? null : branding.logoBytes(), "school-logo");
                if (logo == null) logo = bundledBbcLogo(doc);
                PDImageXObject stamp = imageOrNull(doc, branding == null ? null : branding.stampBytes(), "school-stamp");
                float y = header(doc, page, b, reportFrench, image, secondary, branding, logo);
                // A card is a single sheet whenever it can be: row heights are derived
                // from the subject count so table, totals, summary and signatures all fit.
                // Cards with too many subjects fall back to the paginated layout.
                boolean annualSecondary = secondary && annual;
                float sheetRow = annualSecondary ? sheetRowHeight(b.lines().size(), 34)
                        : computed ? sheetRowHeight(b.lines().size(), annual ? 18 : 0) : 0;
                if (sheetRow < SHEET_ROW_MIN) sheetRow = 0;
                float[] termRows = secondary && !annual ? secondaryTermRowHeights(b, reportFrench) : null;
                y = secondary ? secondaryTableHeader(doc, page, y, reportFrench, annual, templateFamily)
                        : computed ? computedTableHeader(doc, page, y, reportFrench, b)
                        : tableHeader(doc, page, y, reportFrench, false, annual, templateFamily);
                for (int lineIndex = 0; lineIndex < b.lines().size(); lineIndex++) {
                    BulletinLineView line = b.lines().get(lineIndex);
                    float rowHeight = annualSecondary ? (sheetRow > 0 ? sheetRow : SHEET_ROW_MAX)
                            : secondary ? termRows[lineIndex]
                            : computed ? (sheetRow > 0 ? sheetRow : computedRowHeight(line, b)) : 22;
                    boolean shade = lineIndex % 2 == 1;
                    if (y - rowHeight < 78) {
                        footer(doc, page, reportFrench, false);
                        page = new PDPage(PDRectangle.A4); doc.addPage(page);
                        y = header(doc, page, b, reportFrench, image, secondary, branding, logo);
                        y = secondary ? secondaryTableHeader(doc, page, y, reportFrench, annual, templateFamily)
                                : computed ? computedTableHeader(doc, page, y, reportFrench, b)
                                : tableHeader(doc, page, y, reportFrench, false, annual, templateFamily);
                    }
                    if (annualSecondary) y -= secondaryAnnualRow(doc, page, y, line, reportFrench, rowHeight, shade);
                    else if (secondary) y -= secondaryRow(doc, page, y, line, reportFrench, rowHeight, shade);
                    else if (computed) y -= computedRow(doc, page, y, line, reportFrench, b, sheetRow, shade);
                    else { row(doc, page, y, line, reportFrench, false); y -= 22; }
                }
                y -= 5;
                if (secondary) y -= secondaryTotals(doc, page, y, b, reportFrench, annual);
                else if (computed && annual) y -= computedTotals(doc, page, y, b, reportFrench);
                y -= 6;
                // Summary, gap and the minimum signature cells must stay clear of
                // the footer band; otherwise continue on a fresh page.
                if (y < SUMMARY_HEIGHT + BLOCK_GAP + SIGN_MIN + SIGN_BOTTOM) {
                    footer(doc, page, reportFrench, false); page = new PDPage(PDRectangle.A4); doc.addPage(page);
                    y = header(doc, page, b, reportFrench, image, secondary, branding, logo) - 10;
                }
                summary(doc, page, y, b, reportFrench, secondary);
                signatureBoxes(doc, page, y - SUMMARY_HEIGHT - BLOCK_GAP, reportFrench, branding, stamp);
                // The verification mark lives in the footer band so it never
                // obscures the result table, conduct block, or signatures.
                boolean qr = drawQr(doc, page, b, RIGHT - 44, 5, 44);
                footer(doc, page, reportFrench, qr);
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
        return officialHeader(doc, page, b, fr, branding, logo, secondary);
    }

    /** Header and identity grid shared by official primary and secondary report cards. */
    private float officialHeader(PDDocument doc, PDPage page, BulletinSnapshotView b, boolean fr,
                                 BrandingRenderData branding, PDImageXObject logo, boolean secondary) throws Exception {
        StudentRenderData student = studentData(b);
        String schoolName = branding == null || branding.schoolName() == null || branding.schoolName().isBlank()
                ? "BAYO BILINGUAL COMPLEX" : branding.schoolName();
        String schoolNameEn = branding == null || branding.schoolNameEn() == null || branding.schoolNameEn().isBlank()
                ? schoolName : branding.schoolNameEn();
        String ministry = branding == null || branding.ministryText() == null || branding.ministryText().isBlank()
                ? (secondary ? "MINISTÈRE DES ENSEIGNEMENTS SECONDAIRES" : "MINISTÈRE DE L'ÉDUCATION DE BASE")
                : branding.ministryText();
        String delegation = branding == null || branding.delegationText() == null || branding.delegationText().isBlank()
                ? "DÉLÉGATION RÉGIONALE / DÉPARTEMENTALE" : branding.delegationText();
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Three fixed masthead columns prevent the two national headings from
            // drifting into the centre identity block. The English block mirrors
            // the French block by being aligned to the right page edge.
            final float leftHead = LEFT;
            final float leftHeadWidth = 170;
            final float rightHead = RIGHT;
            final float rightHeadWidth = 170;
            textFitted(cs, bold(), 7, leftHead, 814, "RÉPUBLIQUE DU CAMEROUN", leftHeadWidth);
            text(cs, normal(), 6, leftHead + 18, 804, "Paix-Travail-Patrie");
            textFitted(cs, bold(), 6, leftHead, 791, ministry, leftHeadWidth);
            textFitted(cs, normal(), 6, leftHead, 781, delegation, leftHeadWidth);
            textRight(cs, bold(), 7, rightHead, 814, "REPUBLIC OF CAMEROON", rightHeadWidth);
            textRight(cs, normal(), 6, rightHead - 18, 804, "Peace-Work-Fatherland", rightHeadWidth - 18);
            textRight(cs, bold(), 6, rightHead, 791,
                    secondary ? "MINISTRY OF SECONDARY EDUCATION" : "MINISTRY OF BASIC EDUCATION", rightHeadWidth);
            textRight(cs, normal(), 6, rightHead, 781, "REGIONAL / DIVISIONAL DELEGATION", rightHeadWidth);
            drawContainedLogo(cs, logo, (LEFT + RIGHT) / 2f, 802, 44, 44);
            fill(cs, NAVY);
            textCentered(cs, bold(), 8, 190, 405, 766, fr ? schoolName : schoolNameEn);
            cs.setNonStrokingColor(0, 0, 0);
            textCentered(cs, normal(), 6, 190, 405, 755, schoolLocation(branding));
            line(cs, LEFT, 744, RIGHT, 744, 1f);

            String title = reportTitle(b, fr);
            fill(cs, NAVY);
            textCentered(cs, bold(), 13, LEFT, RIGHT, 726, title);
            fill(cs, GOLD);
            textCentered(cs, bold(), 9, LEFT, RIGHT, 713,
                    (fr ? "ANNÉE SCOLAIRE : " : "SCHOOL YEAR : ") + student.schoolYear());
            cs.setNonStrokingColor(0, 0, 0);

            float top = 700;
            float bottom = 632;
            box(cs, LEFT, bottom, RIGHT, top);
            line(cs, 360, bottom, 360, top, .45f);
            line(cs, LEFT, 683, RIGHT, 683, .35f);
            line(cs, LEFT, 666, RIGHT, 666, .35f);
            line(cs, LEFT, 649, RIGHT, 649, .35f);
            textFitted(cs, bold(), 6.5f, 48, 689, fr ? "NOM ET PRÉNOMS" : "NAME OF STUDENT", 88);
            textFitted(cs, bold(), 8, 142, 689, b.studentName(), 212);
            textFitted(cs, bold(), 6.5f, 368, 689, fr ? "CLASSE" : "CLASS", 50);
            textFitted(cs, bold(), 8, 424, 689, b.className(), 121);
            textFitted(cs, normal(), 7, 48, 672, (fr ? "NÉ(E) LE / À : " : "BORN ON / AT: ") + student.birth(), 304);
            textFitted(cs, normal(), 7, 368, 672, (fr ? "MATRICULE : " : "STUDENT ID: ") + safeText(b.matricule()), 177);
            textFitted(cs, normal(), 7, 48, 655, (fr ? "SEXE : " : "GENDER: ") + student.sex()
                    + "     " + (fr ? "REDOUBLANT(E) : " : "REPEATER: ") + repeaterLabel(student.repeater(), fr), 304);
            textFitted(cs, normal(), 7, 368, 655, (fr ? "EFFECTIF : " : "CLASS ENROLMENT: ") + b.classSize(), 177);
            textFitted(cs, normal(), 6.5f, 48, 638, (fr ? "PARENT / TUTEUR : " : "PARENT / GUARDIAN: ") + student.parent(), 304);
            textFitted(cs, normal(), 6.5f, 368, 638,
                    (fr ? "PROF. TITULAIRE : " : "CLASS MASTER: ") + student.classMaster(), 177);
        }
        return 616;
    }

    private float tableHeader(PDDocument doc, PDPage page, float y, boolean fr, boolean secondary,
                              boolean annual, String templateFamily) throws Exception {
        if (secondary) return secondaryTableHeader(doc, page, y, fr, annual, templateFamily);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            fill(cs, NAVY);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22);
            cs.fill();
            cs.setNonStrokingColor(1f, 1f, 1f);
            text(cs, bold(), 8, 48, y - 10, fr ? "MATIÈRE" : "SUBJECT");
            text(cs, bold(), 8, 208, y - 10, fr ? "NOTE" : "MARK");
            text(cs, bold(), 7, 266, y - 10, fr ? "COMPOSANTES" : "COMPONENTS");
            text(cs, bold(), 8, 390, y - 10, fr ? "APPRÉCIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    /**
     * Column geometry of the primary/kindergarten result table: subject cell, one cell per
     * contributing period, the current result, then the remark. Shared by header, rows and totals.
     */
    private record ComputedColumns(float subjectRight, float currentRight, float remarkLeft, float componentWidth) {
        static ComputedColumns of(int periods) {
            float subjectRight = 225;
            float currentRight = subjectRight + Math.max(56, 44 * Math.max(1, periods));
            float componentWidth = periods == 0 ? 0 : (currentRight - subjectRight) / periods;
            return new ComputedColumns(subjectRight, currentRight, currentRight + 56, componentWidth);
        }
        float periodLeft(int index) { return subjectRight + index * componentWidth; }
    }

    /** Primary and kindergarten computed results are an ordinary mean, never weighted. */
    private float computedTableHeader(PDDocument doc, PDPage page, float y, boolean fr,
                                      BulletinSnapshotView bulletin) throws Exception {
        List<String> periods = dependencyCodes(bulletin);
        ComputedColumns c = ComputedColumns.of(periods.size());
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            fill(cs, NAVY);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(1f, 1f, 1f);
            text(cs, bold(), 7, 48, y - 10, fr ? "MATIÈRE" : "SUBJECT");
            for (int i = 0; i < periods.size(); i++) textCentered(cs, bold(), 7,
                    c.periodLeft(i), c.periodLeft(i + 1), y - 10, displayPeriodCode(periods.get(i)));
            textCentered(cs, bold(), 7, c.currentRight(), c.remarkLeft(), y - 10,
                    bulletin.product() != null && bulletin.product().equalsIgnoreCase("ANNUAL") ? (fr ? "ANNUEL" : "ANNUAL") : (fr ? "TERME" : "TERM"));
            textFitted(cs, bold(), 7, c.remarkLeft() + 4, y - 10, fr ? "APPRÉCIATION" : "REMARK", RIGHT - c.remarkLeft() - 8);
            cs.setNonStrokingColor(0, 0, 0);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    /** Draw one primary row. {@code fixedHeight > 0} lays the card out as a single compact sheet row. */
    private float computedRow(PDDocument doc, PDPage page, float y, BulletinLineView line,
                              boolean fr, BulletinSnapshotView bulletin, float fixedHeight, boolean shade) throws Exception {
        List<String> periods = dependencyCodes(bulletin);
        ComputedColumns c = ComputedColumns.of(periods.size());
        float height = 24;
        String remark = line.teacherRemark() == null ? appreciation(line.appreciation(), fr) : line.teacherRemark();
        if (remark != null && remark.length() > 26) height = 32;
        if (fixedHeight > 0) height = fixedHeight;
        float base = y - height / 2f - 2.5f;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            if (shade) { fill(cs, ZEBRA); cs.addRect(LEFT, y - height, RIGHT - LEFT, height); cs.fill(); cs.setNonStrokingColor(0, 0, 0); }
            // Long English and French subject names shrink, then wrap onto a second
            // line, so they stay complete without entering the period-result columns.
            drawCell(cs, 48, y, height, layoutCell(bold(), 7, 5.5f, c.subjectRight() - 54, line.subjectLabel(), 2, height - 2), null);
            for (int i = 0; i < periods.size(); i++) {
                String periodCode = periods.get(i);
                BigDecimal mark = line.periodMarks() == null ? null : line.periodMarks().stream()
                        .filter(value -> periodCode.equals(value.periodCode())).map(PeriodMarkView::mark).findFirst().orElse(null);
                textCentered(cs, normal(), 8, c.periodLeft(i), c.periodLeft(i + 1), base, number(mark));
            }
            textCentered(cs, bold(), 8, c.currentRight(), c.remarkLeft(), base, number(line.mark()));
            drawCell(cs, c.remarkLeft() + 4, y, height, layoutCell(normal(), 7, 6, RIGHT - c.remarkLeft() - 8, remark,
                    3, height - 2), null);
            List<Float> boundaries = new java.util.ArrayList<>();
            boundaries.add(LEFT); boundaries.add(c.subjectRight());
            for (int i = 0; i < periods.size(); i++) boundaries.add(c.periodLeft(i + 1));
            boundaries.add(c.remarkLeft());
            boundaries.add(RIGHT);
            for (float x : boundaries) hairline(cs, x, y, x, y - height);
            hairline(cs, LEFT, y - height, RIGHT, y - height);
        }
        return height;
    }

    /** Mean row closing an annual primary table: one ordinary mean per contributing period. */
    private float computedTotals(PDDocument doc, PDPage page, float y, BulletinSnapshotView bulletin,
                                 boolean fr) throws Exception {
        List<String> periods = dependencyCodes(bulletin);
        ComputedColumns c = ComputedColumns.of(periods.size());
        float height = 18;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            fill(cs, TINT); cs.addRect(LEFT, y - height, RIGHT - LEFT, height); cs.fill();
            cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, y - height, RIGHT, y);
            text(cs, bold(), 8, 48, y - 12, fr ? "MOYENNE" : "AVERAGE");
            for (int i = 0; i < periods.size(); i++) {
                String code = periods.get(i);
                BigDecimal sum = BigDecimal.ZERO; int count = 0;
                for (BulletinLineView line : bulletin.lines()) {
                    BigDecimal mark = line.periodMarks() == null ? null : line.periodMarks().stream()
                            .filter(value -> code.equals(value.periodCode())).map(PeriodMarkView::mark).findFirst().orElse(null);
                    if (mark != null) { sum = sum.add(mark); count++; }
                }
                textCentered(cs, normal(), 8, c.periodLeft(i), c.periodLeft(i + 1), y - 12,
                        count == 0 ? "—" : number(sum.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)));
            }
            textCentered(cs, bold(), 8, c.currentRight(), c.remarkLeft(), y - 12, number(bulletin.average()));
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

    /** Compact technical period codes so narrow primary result columns stay readable. */
    private static String displayPeriodCode(String code) {
        String normalized = safeText(code).toUpperCase(Locale.ROOT).replace("_RESULT", "");
        java.util.regex.Matcher sequence = java.util.regex.Pattern.compile(".*?([1-6]).*").matcher(normalized);
        if (normalized.startsWith("S") && sequence.matches()) return "S" + sequence.group(1);
        if (normalized.startsWith("T") && sequence.matches()) return "T" + sequence.group(1);
        return clip(normalized, 6);
    }

    private static final float[] SECONDARY_ANNUAL_COLUMNS = {42, 190, 220, 250, 280, 312, 338, 374, 404, 446, 553};
    private static final float[] SECONDARY_TERM_COLUMNS = {42, 160, 280, 308, 336, 362, 396, 424, 462, 553};

    private float secondaryTableHeader(PDDocument doc, PDPage page, float y, boolean fr,
                                       boolean annual, String templateFamily) throws Exception {
        float[] col = annual ? SECONDARY_ANNUAL_COLUMNS : SECONDARY_TERM_COLUMNS;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            fill(cs, NAVY);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(1f, 1f, 1f);
            textFitted(cs, bold(), 7, 48, y - 10, fr ? "MATIÈRE / PROF." : "SUBJECT / TEACHER", col[1] - 54);
            if (annual) {
                String[] labels = {"T1", "T2", "T3", fr ? "MOY" : "AV", "COEF", fr ? "PROD" : "PRODUCT",
                        fr ? "COTE" : "GRADE", "MIN-MAX"};
                for (int i = 0; i < labels.length; i++) textCentered(cs, bold(), 6, col[i + 1], col[i + 2], y - 10, labels[i]);
                textFitted(cs, bold(), 6, col[9] + 6, y - 10, fr ? "APPRÉCIATION / VISA" : "REMARK / SIGNATURE", col[10] - col[9] - 10);
            } else {
                textFitted(cs, bold(), 6, col[1] + 6, y - 10, fr ? "COMPÉTENCES ÉVALUÉES" : "COMPETENCIES EVALUATED", col[2] - col[1] - 10);
                String[] labels = {"N/20", "M/20", "COEF", fr ? "PROD" : "PRODUCT", fr ? "COTE" : "GRADE", "MIN-MAX"};
                for (int i = 0; i < labels.length; i++) textCentered(cs, bold(), i == 5 ? 5.5f : 6, col[i + 2], col[i + 3], y - 10, labels[i]);
                textFitted(cs, bold(), 6, col[8] + 5, y - 10, fr ? "APPRÉCIATION" : "REMARK", col[9] - col[8] - 9);
            }
            cs.setNonStrokingColor(0, 0, 0);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    /** Laid-out text of one secondary term row: subject, teacher, competency evidence and remark. */
    private record SecondaryTermCells(CellLayout subject, CellLayout teacher, List<CellLayout> competencies,
                                      List<String> marks, CellLayout remark) {
        float competencyHeight() { return (float) competencies.stream().mapToDouble(CellLayout::height).sum(); }
        float contentHeight() {
            float subjectBlock = subject.height() + (teacher == null ? 0 : teacher.height() + 0.8f);
            return Math.max(subjectBlock, Math.max(competencyHeight(), remark.height())) + CELL_PAD;
        }
    }

    /**
     * Lay out a secondary term row for a cell of {@code rowHeight}. {@code tight} uses the smallest
     * font sizes and so measures the least height the row can be drawn in.
     */
    private SecondaryTermCells secondaryTermCells(BulletinLineView l, boolean fr, float rowHeight, boolean tight) throws Exception {
        float[] col = SECONDARY_TERM_COLUMNS;
        float subjectWidth = col[1] - 54;
        float room = rowHeight - CELL_PAD;
        String teacher = l.teacherName() == null ? "" : l.teacherName().trim();
        List<CellLayout> competencies = new java.util.ArrayList<>();
        List<String> marks = new java.util.ArrayList<>();
        if (l.assessments() != null) {
            for (AssessmentEvidenceView a : l.assessments()) {
                String label = a.label() == null || a.label().isBlank() ? a.code() : a.label();
                competencies.add(layoutCell(normal(), 6, 6, col[2] - col[1] - 10, label, 3, Float.MAX_VALUE));
                marks.add(a.mark() == null ? "-" : number(a.mark()));
            }
        }
        if (competencies.isEmpty()) {
            competencies.add(layoutCell(normal(), 6, 6, col[2] - col[1] - 10, componentText(l), 3, Float.MAX_VALUE));
            marks.add(number(l.mark()));
        }
        CellLayout teacherCell = teacher.isEmpty() ? null
                : layoutCell(normal(), tight ? 5.5f : 6, 5.5f, subjectWidth, teacher.toUpperCase(Locale.ROOT), 1, Float.MAX_VALUE);
        float teacherHeight = teacherCell == null ? 0 : teacherCell.height() + 0.8f;
        CellLayout subject = layoutCell(bold(), tight ? 5.5f : 7, 5.5f, subjectWidth, l.subjectLabel(), 2, room - teacherHeight);
        if (!subject.complete()) subject = layoutCell(bold(), tight ? 5.5f : 7, 5.5f, subjectWidth, l.subjectLabel(), 2, room);
        String remark = l.teacherRemark() == null ? appreciation(l.appreciation(), fr) : l.teacherRemark();
        return new SecondaryTermCells(subject, teacherCell, competencies, marks,
                layoutCell(normal(), tight ? 5.5f : 6.5f, 5.5f, col[9] - col[8] - 9, remark, 3, room));
    }

    /** Draw a secondary term/sequence row in a cell of the given height; text wraps by rendered width. */
    private float secondaryRow(PDDocument doc, PDPage page, float y, BulletinLineView l, boolean fr,
                               float height, boolean shade) throws Exception {
        float[] col = SECONDARY_TERM_COLUMNS;
        SecondaryTermCells cells = secondaryTermCells(l, fr, height, false);
        float mid = y - height / 2f - 2.5f;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            if (shade) { fill(cs, ZEBRA); cs.addRect(LEFT, y - height, RIGHT - LEFT, height); cs.fill(); cs.setNonStrokingColor(0, 0, 0); }
            drawCell(cs, 48, y, height, cells.subject(), cells.teacher());
            // Each competency's mark sits on the first line of its (possibly wrapped) label.
            float at = y - (height - cells.competencyHeight()) / 2f;
            for (int i = 0; i < cells.competencies().size(); i++) {
                CellLayout competency = cells.competencies().get(i);
                drawCell(cs, col[1] + 6, at, competency.height(), competency, null);
                textCentered(cs, normal(), 6, col[2], col[3], at - 0.92f * competency.size(), cells.marks().get(i));
                at -= competency.height();
            }
            textCentered(cs, bold(), 7.5f, col[3], col[4], mid, number(l.mark()));
            textCentered(cs, normal(), 7.5f, col[4], col[5], mid, String.valueOf(l.coefficient()));
            textCentered(cs, normal(), 7.5f, col[5], col[6], mid, number(l.weighted()));
            textCentered(cs, bold(), 7, col[6], col[7], mid, grade(l.mark()));
            textCentered(cs, normal(), 7, col[7], col[8], mid, "—");
            drawCell(cs, col[8] + 5, y, height, cells.remark(), null);
            for (float x : col) hairline(cs, x, y, x, y - height);
            hairline(cs, LEFT, y - height, RIGHT, y - height);
        }
        return height;
    }

    /**
     * Draw one annual secondary row in a fixed-height cell. The teacher goes under the subject when
     * there is room, otherwise after it. Long subjects wrap onto a second line instead of being cut.
     */
    private float secondaryAnnualRow(PDDocument doc, PDPage page, float y, BulletinLineView l, boolean fr,
                                     float height, boolean shade) throws Exception {
        float[] col = SECONDARY_ANNUAL_COLUMNS;
        String teacher = l.teacherName() == null ? "" : l.teacherName().trim();
        float mid = y - height / 2f - 2.5f;
        String remark = l.teacherRemark() == null ? appreciation(l.appreciation(), fr) : l.teacherRemark();
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            if (shade) { fill(cs, ZEBRA); cs.addRect(LEFT, y - height, RIGHT - LEFT, height); cs.fill(); cs.setNonStrokingColor(0, 0, 0); }
            float subjectWidth = col[1] - 48 - 6;
            CellLayout teacherCell = teacher.isEmpty() ? null
                    : layoutCell(normal(), 6, 5.5f, subjectWidth, teacher.toUpperCase(Locale.ROOT), 1, Float.MAX_VALUE);
            float teacherHeight = teacherCell == null ? 0 : teacherCell.height() + 0.8f;
            CellLayout subject = layoutCell(bold(), 7, 5.5f, subjectWidth, l.subjectLabel(), 2, height - CELL_PAD - teacherHeight);
            if (!subject.complete()) subject = layoutCell(bold(), 7, 5.5f, subjectWidth, l.subjectLabel(), 2, height - CELL_PAD);
            drawCell(cs, 48, y, height, subject, teacherCell);
            List<PeriodMarkView> marks = annualPeriodMarks(l);
            for (int i = 0; i < 3; i++) textCentered(cs, normal(), 7, col[i + 1], col[i + 2], mid,
                    number(i < marks.size() ? marks.get(i).mark() : null));
            textCentered(cs, bold(), 7, col[4], col[5], mid, number(l.mark()));
            textCentered(cs, normal(), 8, col[5], col[6], mid, String.valueOf(l.coefficient()));
            textCentered(cs, normal(), 8, col[6], col[7], mid, number(l.weighted()));
            textCentered(cs, bold(), 7, col[7], col[8], mid, grade(l.mark()));
            textCentered(cs, normal(), 7, col[8], col[9], mid, "—");
            drawCell(cs, col[9] + 6, y, height, layoutCell(normal(), 6.5f, 5.5f, col[10] - col[9] - 10, remark, 2, height - CELL_PAD), null);
            for (float x : col) hairline(cs, x, y, x, y - height);
            hairline(cs, LEFT, y - height, RIGHT, y - height);
        }
        return height;
    }

    /** Height available to table rows when totals, summary and signatures share the sheet. */
    private static float sheetBudget(float totalsHeight) {
        float reserved = 5 + totalsHeight + 6 + SUMMARY_HEIGHT + BLOCK_GAP + SIGN_MIN + SIGN_BOTTOM;
        // 1pt of slack: n float row heights summed can overshoot the budget by a
        // rounding error and push the summary onto a second page.
        return TABLE_TOP - reserved - 1;
    }

    /** Uniform row height that lets a card's table, totals, summary and signatures share one A4 sheet. */
    private static float sheetRowHeight(int lines, float totalsHeight) {
        return Math.min(SHEET_ROW_MAX, sheetBudget(totalsHeight) / Math.max(1, lines));
    }

    /**
     * Row heights of a secondary term card. Rows keep their natural height when the card fits one sheet;
     * otherwise they tighten towards their content height, and the card paginates only if that is not enough.
     */
    private float[] secondaryTermRowHeights(BulletinSnapshotView b, boolean fr) throws Exception {
        int n = b.lines().size();
        float[] natural = new float[n], compact = new float[n];
        float sumNatural = 0, sumCompact = 0;
        for (int i = 0; i < n; i++) {
            BulletinLineView line = b.lines().get(i);
            natural[i] = Math.max(22, secondaryTermCells(line, fr, Float.MAX_VALUE, false).contentHeight() + 3);
            compact[i] = Math.min(natural[i], Math.max(SHEET_ROW_MIN, secondaryTermCells(line, fr, Float.MAX_VALUE, true).contentHeight()));
            sumNatural += natural[i]; sumCompact += compact[i];
        }
        float budget = sheetBudget(18);
        if (sumNatural <= budget || sumCompact > budget) return natural;
        float t = (budget - sumCompact) / (sumNatural - sumCompact);
        for (int i = 0; i < n; i++) natural[i] = compact[i] + (natural[i] - compact[i]) * t;
        return natural;
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
            fill(cs, TINT); cs.addRect(LEFT, y - 18, RIGHT - LEFT, 18); cs.fill();
            cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, y - 18, RIGHT, y);
            if (annual) {
                float[] col = SECONDARY_ANNUAL_COLUMNS;
                for (int i = 5; i <= 7; i++) line(cs, col[i], y - 18, col[i], y, .45f);
                textCentered(cs, bold(), 8, LEFT, col[5], y - 12, "TOTAL");
                textCentered(cs, bold(), 7.5f, col[5], col[6], y - 12, String.valueOf(coefficient));
                textCentered(cs, bold(), 7.5f, col[6], col[7], y - 12, number(product));
                textCentered(cs, bold(), 8, col[7], RIGHT, y - 12, (fr ? "Moyenne : " : "Student average: ") + number(bulletin.average()));
                fill(cs, TINT); cs.addRect(LEFT, y - 34, RIGHT - LEFT, 16); cs.fill();
                cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, y - 34, RIGHT, y - 18);
                float segment = (RIGHT - 210) / 3f;
                line(cs, 210, y - 34, 210, y - 18, .4f);
                line(cs, 210 + segment, y - 34, 210 + segment, y - 18, .4f);
                line(cs, 210 + 2 * segment, y - 34, 210 + 2 * segment, y - 18, .4f);
                text(cs, bold(), 6, 48, y - 29, fr ? "RAPPEL DES MOYENNES TRIMESTRIELLES" : "TERM AVERAGES");
                List<String> periodCodes = annualPeriodCodes(bulletin);
                for (int index = 0; index < 3; index++) {
                    BigDecimal average = index < periodCodes.size() ? weightedPeriodAverage(bulletin, periodCodes.get(index)) : null;
                    String label = fr ? (index + 1) + (index == 0 ? "er" : "e") + " Trimestre" : switch (index) { case 0 -> "1st Term"; case 1 -> "2nd Term"; default -> "3rd Term"; };
                    text(cs, bold(), 6, 218 + index * segment, y - 29, label + " : " + number(average));
                }
            } else {
                float[] col = SECONDARY_TERM_COLUMNS;
                for (int i = 4; i <= 6; i++) line(cs, col[i], y - 18, col[i], y, .45f);
                textCentered(cs, bold(), 8, LEFT, col[4], y - 12, "TOTAL");
                textCentered(cs, bold(), 7.5f, col[4], col[5], y - 12, String.valueOf(coefficient));
                textCentered(cs, bold(), 7.5f, col[5], col[6], y - 12, number(product));
                textCentered(cs, bold(), 8, col[6], RIGHT, y - 12, (fr ? "Moyenne : " : "Average: ") + number(bulletin.average()));
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
        if (secondary) { secondaryRow(doc, page, y, l, fr, Math.max(22, secondaryTermCells(l, fr, Float.MAX_VALUE, false).contentHeight() + 3), false); return; }
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            String subject = l.subjectGroupLabel() == null || l.subjectGroupLabel().isBlank()
                    ? l.subjectLabel() : l.subjectGroupLabel() + " / " + l.subjectLabel();
            text(cs, bold(), 8, 48, y - 14, clip(subject, 24));
            text(cs, normal(), 9, 208, y - 14, number(l.mark()));
            text(cs, normal(), 7, 266, y - 14, clip(componentText(l), 22));
            String remark = l.teacherRemark() == null ? appreciation(l.appreciation(), fr) : l.teacherRemark();
            if (l.teacherName() != null && !l.teacherName().isBlank()) remark = "Prof. " + l.teacherName() + " · " + remark;
            text(cs, normal(), 7, 390, y - 14, clip(remark, 29));
            for (float x : new float[]{42, 200, 250, 380, 553}) {
                line(cs, x, y, x, y - 22, 0.35f);
            }
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
        String conductText = conduct == null ? "—" : (conduct.honorRoll() ? "Honor roll " : "")
                + (conduct.encouragement() ? "Encouragement " : "")
                + (conduct.congratulations() ? "Congratulations " : "")
                + (conduct.workWarning() ? "Work warning " : "")
                + (conduct.workBlame() ? "Work blame " : "")
                + (conduct.conductWarning() ? "Conduct warning " : "")
                + (conduct.conductBlame() ? "Conduct blame" : "");
        if (conductText.isBlank()) conductText = fr ? "Aucune distinction" : "No distinction";
        String decision = councilDecision(conduct, fr);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            float x1 = 214, x2 = 384, titleHeight = 16, rowHeight = (SUMMARY_HEIGHT - titleHeight) / 6f;
            float headerBottom = y - titleHeight, bottom = y - SUMMARY_HEIGHT;
            fill(cs, TINT); cs.addRect(LEFT, headerBottom, RIGHT - LEFT, titleHeight); cs.fill();
            cs.setNonStrokingColor(0, 0, 0); box(cs, LEFT, bottom, RIGHT, y);
            line(cs, x1, bottom, x1, y, .45f); line(cs, x2, bottom, x2, y, .45f);
            for (int row = 0; row <= 5; row++) hairline(cs, LEFT, headerBottom - row * rowHeight, RIGHT, headerBottom - row * rowHeight);
            fill(cs, NAVY);
            textCentered(cs, bold(), 7.5f, LEFT, x1, y - 11, fr ? "DISCIPLINE" : "DISCIPLINE");
            textCentered(cs, bold(), 7.5f, x1, x2, y - 11, fr ? "TRAVAIL DE L'ÉLÈVE" : "STUDENT PERFORMANCE");
            textCentered(cs, bold(), 7.5f, x2, RIGHT, y - 11, fr ? "PROFIL DE LA CLASSE" : "CLASS PROFILE");
            cs.setNonStrokingColor(0, 0, 0);

            String unexcused = b.attendance() == null ? "0" : number(b.attendance().unjustifiedAbsenceHours());
            String excused = b.attendance() == null ? "0" : number(b.attendance().justifiedAbsenceHours());
            String late = b.attendance() == null ? "0" : number(BigDecimal.valueOf(b.attendance().lateMinutes()).divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
            String[] disciplineLabels = fr ? new String[]{"Absences non justifiées","Absences justifiées","Retards (heures)","Avertissement de conduite","Blâme de conduite","Exclusion (jours)"}
                    : new String[]{"Unjustified absences","Justified absences","Lateness (hours)","Conduct warning","Reprimand","Suspension (days)"};
            String[] disciplineValues = new String[]{unexcused,excused,late,
                    conduct != null && conduct.conductWarning() ? "X" : "—",
                    conduct != null && conduct.conductBlame() ? "X" : "—",
                    conduct == null ? "0" : String.valueOf(conduct.exclusionDays())};
            BigDecimal totalScore = secondary
                    ? b.lines().stream().map(BulletinLineView::weighted).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    : b.lines().stream().map(BulletinLineView::mark).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String[] workLabels = secondary
                    ? (fr ? new String[]{"Total général","Total coef.","Moyenne","Cote","Décision du conseil de classe","Distinctions"}
                    : new String[]{"Total score","Total coef.","Average","Grade","Class council decision","Awards"})
                    : (fr ? new String[]{"Total des notes","Moyenne","Cote","Décision du conseil de classe","Distinctions",""}
                    : new String[]{"Total marks","Average","Grade","Class council decision","Awards",""});
            String[] workValues = secondary
                    ? new String[]{number(totalScore),String.valueOf(b.lines().stream().mapToInt(BulletinLineView::coefficient).sum()),
                    number(b.average()),grade(b.average()),decision,conductText}
                    : new String[]{number(totalScore),number(b.average()),grade(b.average()),decision,conductText,""};
            String[] classLabels = fr ? new String[]{"Moyenne de la classe","Rang","Nombre de moyennes","Taux de réussite","Minimum","Maximum"}
                    : new String[]{"Class average","Rank","Number passed","Success rate","Minimum","Maximum"};
            String[] classValues = new String[]{stats == null ? "—" : number(stats.average()),
                    (b.rank() == null ? "—" : b.rank()) + " / " + b.classSize(),
                    stats == null ? "—" : String.valueOf(stats.successCount()), stats == null ? "—" : number(stats.successRate()) + "%",
                    stats == null ? "—" : number(stats.minimum()), stats == null ? "—" : number(stats.maximum())};
            String[][] labels = {disciplineLabels, workLabels, classLabels};
            String[][] values = {disciplineValues, workValues, classValues};
            float[] left = {LEFT, x1, x2}, right = {x1, x2, RIGHT};
            for (int row = 0; row < 6; row++) {
                float ty = headerBottom - rowHeight / 2f - 2.2f - row * rowHeight;
                for (int column = 0; column < 3; column++) {
                    if (labels[column][row].isEmpty()) continue;
                    // The label keeps its natural width; the value takes whatever is left of the panel.
                    float inner = right[column] - left[column] - 12;
                    float labelWidth = Math.min(inner - 30,
                            normal().getStringWidth(safeText(labels[column][row])) / 1000f * 6.5f);
                    float valueWidth = inner - labelWidth - 6;
                    float valueRight = right[column] - 6;
                    fill(cs, MUTED);
                    textFitted(cs, normal(), 6.5f, left[column] + 6, ty, labels[column][row], labelWidth + 1);
                    cs.setNonStrokingColor(0, 0, 0);
                    textRight(cs, bold(), fittedSize(bold(), 6.5f, 5.5f, values[column][row], valueWidth),
                            valueRight, ty, values[column][row], valueWidth);
                }
            }
        }
    }

    private void signatureBoxes(PDDocument doc, PDPage page, float top, boolean fr,
                                BrandingRenderData branding, PDImageXObject stamp) throws Exception {
        float x1 = 225, x2 = 335, x3 = 445;
        // Fill the free space down to the footer band, within sensible bounds.
        float height = Math.max(SIGN_MIN, Math.min(SIGN_MAX, top - SIGN_BOTTOM));
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            box(cs, LEFT, top - height, RIGHT, top);
            line(cs, x1, top - height, x1, top, .5f); line(cs, x2, top - height, x2, top, .5f); line(cs, x3, top - height, x3, top, .5f);
            fill(cs, NAVY);
            textShrunk(cs, bold(), 6.5f, 5.5f, LEFT + 6, top - 12,
                    fr ? "APPRÉCIATION DU TRAVAIL DE L'ÉLÈVE" : "REMARKS ON STUDENT PERFORMANCE", x1 - LEFT - 12);
            textShrunk(cs, bold(), 6.5f, 5.5f, x1 + 7, top - 12,
                    fr ? "Visa du parent / tuteur" : "Parent / Guardian signature", x2 - x1 - 14);
            textShrunk(cs, bold(), 6.5f, 5.5f, x2 + 7, top - 12,
                    fr ? "Nom et visa du professeur" : "Class master's signature", x3 - x2 - 14);
            textShrunk(cs, bold(), 6.5f, 5.5f, x3 + 7, top - 12,
                    fr ? "Fait à " + schoolCity(branding) + ", le" : "At " + schoolCity(branding) + ", on", RIGHT - x3 - 14);
            // The head-of-school title sits at the foot of its cell, under the date line;
            // a stamp needs reserved space, otherwise the title may use the full width.
            String headTitle = branding == null || branding.principalTitle() == null || branding.principalTitle().isBlank()
                    ? (fr ? "CHEF D'ÉTABLISSEMENT" : "HEAD OF SCHOOL") : branding.principalTitle();
            float headTitleWidth = stamp == null ? RIGHT - x3 - 14 : 46;
            textShrunk(cs, bold(), 6.5f, 5.5f, x3 + 7, top - height + 8, headTitle, headTitleWidth);
            cs.setNonStrokingColor(0, 0, 0);
            if (stamp != null) cs.drawImage(stamp, x3 + 55, top - height + 6, 34, 34);
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
                                   FROM class_teacher_assignment a
                                   JOIN employee e ON e.id=a.employee_id AND e.school_id=a.school_id
                                  WHERE a.school_id=s.school_id AND a.academic_session_id=?
                                    AND a.class_id=coalesce(?, (SELECT se.school_class_id
                                          FROM student_enrollment se
                                         WHERE se.school_id=s.school_id AND se.student_id=s.id
                                           AND se.academic_session_id=a.academic_session_id
                                           AND se.status='ACTIVE'
                                         ORDER BY se.enrolled_on DESC,se.created_at DESC LIMIT 1))
                                    AND a.role='HOMEROOM' AND a.status='ACTIVE'
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
                    bulletin.academicSessionId(), bulletin.programmeClassId(), bulletin.academicSessionId(),
                    bulletin.studentId(), TenantContext.get());
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

    /**
     * New report-card snapshots may intentionally have no frozen branding yet.
     * In that case use the school's configured address rather than an old sample
     * location embedded in the renderer.
     */
    private String schoolLocation(BrandingRenderData branding) {
        String configured = blankJoin(branding == null ? null : branding.address(),
                branding == null ? null : branding.city(), "");
        if (!configured.isBlank()) return configured;
        try {
            List<String> values = jdbc.query("SELECT concat_ws(', ', nullif(address,''), nullif(city,'')) FROM school WHERE id=?",
                    (rs, n) -> rs.getString(1), TenantContext.get());
            if (values != null && !values.isEmpty() && values.getFirst() != null && !values.getFirst().isBlank())
                return values.getFirst();
        } catch (RuntimeException ignored) {
            // The PDF remains printable even when a legacy tenant lacks profile data.
        }
        return "Maroua II";
    }

    private String schoolCity(BrandingRenderData branding) {
        if (branding != null && branding.city() != null && !branding.city().isBlank()) return branding.city();
        try {
            List<String> values = jdbc.query("SELECT city FROM school WHERE id=?", (rs, n) -> rs.getString(1), TenantContext.get());
            if (values != null && !values.isEmpty() && values.getFirst() != null && !values.getFirst().isBlank())
                return values.getFirst();
        } catch (RuntimeException ignored) {
            // The location heading is informational and must never prevent printing.
        }
        return "Maroua II";
    }

    private static PDImageXObject imageOrNull(PDDocument doc, byte[] bytes, String name) {
        if (bytes == null || bytes.length == 0) return null;
        try { return PDImageXObject.createFromByteArray(doc, bytes, name); }
        catch (Exception ignored) { return null; }
    }

    /** Bundled fallback for report cards whose frozen snapshot has no branding image yet. */
    private static PDImageXObject bundledBbcLogo(PDDocument doc) {
        try (InputStream source = ReportCardPdfService.class.getResourceAsStream("/bbc-logo.png")) {
            return source == null ? null : PDImageXObject.createFromByteArray(doc, source.readAllBytes(), "bbc-logo");
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Draw the emblem within the masthead without distorting its aspect ratio. */
    private static void drawContainedLogo(PDPageContentStream cs, PDImageXObject logo, float centerX,
                                          float centerY, float maxWidth, float maxHeight) throws Exception {
        if (logo == null || logo.getWidth() <= 0 || logo.getHeight() <= 0) return;
        float scale = Math.min(maxWidth / logo.getWidth(), maxHeight / logo.getHeight());
        float width = logo.getWidth() * scale;
        float height = logo.getHeight() * scale;
        cs.drawImage(logo, centerX - width / 2f, centerY - height / 2f, width, height);
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

    /** Snapshot appreciation values were historically stored in French. */
    private static String appreciation(String value, boolean french) {
        String clean = safeText(value);
        if (french || clean.isBlank()) return clean;
        return switch (clean) {
            case "Très bien" -> "Very good";
            case "Bien" -> "Good";
            case "Acquis" -> "Acquired";
            case "En cours d'acquisition" -> "In progress";
            case "Excellent" -> "Excellent";
            default -> clean;
        };
    }

    /** Council decision in the card's language; an observation that only repeats the decision is not printed twice. */
    private static String councilDecision(ConductSummaryView conduct, boolean fr) {
        if (conduct == null || conduct.decisionCode() == null || conduct.decisionCode().isBlank()) return "—";
        String code = conduct.decisionCode().trim();
        String label = decisionLabel(code, fr);
        String observation = conduct.councilObservation() == null ? "" : conduct.councilObservation().trim();
        boolean repeated = observation.isEmpty() || observation.equalsIgnoreCase(code)
                || observation.equalsIgnoreCase(decisionLabel(code, true)) || observation.equalsIgnoreCase(decisionLabel(code, false));
        return repeated ? label : label + " / " + observation;
    }

    private static String decisionLabel(String code, boolean fr) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "PROMOTE", "PROMOTED" -> fr ? "Promu(e)" : "Promoted";
            case "REPEAT", "REPEATS" -> fr ? "Redouble" : "Repeats";
            case "REVIEW" -> fr ? "À réviser" : "Under review";
            case "GRADUATE", "GRADUATED" -> fr ? "Diplômé(e)" : "Graduated";
            case "HOLD" -> fr ? "Maintenu(e)" : "Held back";
            default -> code;
        };
    }

    /** Text laid out for a table cell: the font and size chosen, and the lines at that size. */
    private record CellLayout(PDFont font, float size, float width, List<String> lines, boolean complete) {
        float height() { return lines.size() * size * LEADING; }
    }

    /**
     * Lay out cell text: one line, shrunk towards {@code minSize} if needed, unless wrapping onto at most
     * {@code maxLines} lines within {@code maxHeight} keeps it at least 1pt larger. Only text that fits
     * neither way is shortened.
     */
    private static CellLayout layoutCell(PDFont font, float size, float minSize, float width, String value,
                                         int maxLines, float maxHeight) throws Exception {
        CellLayout single = null, wrapped = null;
        for (float s = size; s >= minSize - 0.01f && single == null; s -= 0.5f) {
            if (textWidth(font, s, value) <= width) single = new CellLayout(font, s, width, List.of(safeText(value).trim()), true);
        }
        for (float s = size; s >= minSize - 0.01f && wrapped == null; s -= 0.5f) {
            List<String> lines = wrapLines(font, s, value, width);
            if (lines.size() <= maxLines && lines.size() * s * LEADING <= maxHeight) wrapped = new CellLayout(font, s, width, lines, true);
        }
        if (single != null && (wrapped == null || wrapped.size() < single.size() + 1)) return single;
        if (wrapped != null) return wrapped;
        int fit = Math.max(1, Math.min(maxLines, (int) (maxHeight / (minSize * LEADING))));
        List<String> lines = wrapLines(font, minSize, value, width);
        if (lines.size() > fit) {
            List<String> kept = new java.util.ArrayList<>(lines.subList(0, fit - 1));
            kept.add(fitToWidth(font, minSize, String.join(" ", lines.subList(fit - 1, lines.size())), width));
            lines = kept;
        }
        return new CellLayout(font, minSize, width, lines, false);
    }

    /** Greedy word wrap by rendered width. */
    private static List<String> wrapLines(PDFont font, float size, String value, float width) throws Exception {
        List<String> lines = new java.util.ArrayList<>();
        String current = "";
        for (String word : safeText(value).trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (current.isEmpty() || textWidth(font, size, candidate) <= width) current = candidate;
            else { lines.add(fitToWidth(font, size, current, width)); current = word; }
        }
        lines.add(fitToWidth(font, size, current, width));
        return lines;
    }

    /** Draw a laid-out cell vertically centred; the muted part goes underneath, or after the text when there is no room. */
    private static void drawCell(PDPageContentStream cs, float x, float top, float height, CellLayout main,
                                 CellLayout muted) throws Exception {
        float gap = 0.8f;
        boolean below = muted != null && main.height() + gap + muted.height() <= height - CELL_PAD;
        float at = top - (height - main.height() - (below ? gap + muted.height() : 0)) / 2f;
        for (String line : main.lines()) {
            text(cs, main.font(), main.size(), x, at - 0.92f * main.size(), line);
            at -= main.size() * LEADING;
        }
        if (muted == null) return;
        fill(cs, MUTED);
        if (below) {
            text(cs, muted.font(), muted.size(), x, at - gap - 0.92f * muted.size(), muted.lines().get(0));
        } else {
            float used = textWidth(main.font(), main.size(), main.lines().get(main.lines().size() - 1)) + 5;
            if (main.width() - used >= 16) textFitted(cs, muted.font(), muted.size(), x + used,
                    at + main.size() * LEADING - 0.92f * main.size(), muted.lines().get(0), main.width() - used);
        }
        cs.setNonStrokingColor(0, 0, 0);
    }

    private static float textWidth(PDFont font, float size, String value) throws Exception {
        return font.getStringWidth(safeText(value)) / 1000f * size;
    }

    /** QR payload is deliberately limited to an opaque snapshot checksum/id. */
    private static boolean drawQr(PDDocument doc, PDPage page, BulletinSnapshotView b, float x, float y, float size) {
        String hash = b.snapshotHash() == null ? "" : b.snapshotHash();
        if (b.id() == null || hash.isBlank()) return false;
        try {
            String payload = "/api/public/report-card-verification/" + b.id() + "?checksum=" + hash;
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 96, 96);
            BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
            PDImageXObject image = LosslessFactory.createFromImage(doc, qr);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.drawImage(image, x, y, size, size);
            }
            return true;
        } catch (Exception ignored) { /* QR must never make a bulletin unprintable. */ return false; }
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

    private void footer(PDDocument doc, PDPage page, boolean fr, boolean withQr) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            line(cs, LEFT, FOOTER_LINE, RIGHT, FOOTER_LINE, 0.6f);
            fill(cs, MUTED);
            text(cs, normal(), 6.5f, LEFT, 30, fr ? "Document généré depuis un snapshot académique immuable." : "Generated from an immutable academic snapshot.");
            float right = withQr ? RIGHT - 50 : RIGHT;
            textRight(cs, normal(), 6.5f, right, withQr ? 33 : 30,
                    fr ? "Bayo Bilingual Complex · à conserver sans rature" : "Bayo Bilingual Complex · retain without alteration", right - 200);
            if (withQr) textRight(cs, normal(), 6.5f, right, 23, fr ? "Scanner pour vérifier" : "Scan to verify", 120);
            cs.setNonStrokingColor(0, 0, 0);
        }
    }

    private static void text(PDPageContentStream cs, org.apache.pdfbox.pdmodel.font.PDFont font, float size, float x, float y, String value) throws Exception { cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(safeText(value)); cs.endText(); }
    private static void textFitted(PDPageContentStream cs, PDFont font, float size, float x, float y,
                                   String value, float maxWidth) throws Exception {
        text(cs, font, size, x, y, fitToWidth(font, size, value, maxWidth));
    }
    /** Largest size between {@code minSize} and {@code size} at which the text fits; long labels shrink before they are cut. */
    private static float fittedSize(PDFont font, float size, float minSize, String value, float maxWidth) throws Exception {
        float width = font.getStringWidth(safeText(value)) / 1000f;
        if (width <= 0 || width * size <= maxWidth) return size;
        return Math.max(minSize, Math.min(size, maxWidth / width));
    }
    private static void textShrunk(PDPageContentStream cs, PDFont font, float size, float minSize, float x, float y,
                                   String value, float maxWidth) throws Exception {
        textFitted(cs, font, fittedSize(font, size, minSize, value, maxWidth), x, y, value, maxWidth);
    }
    private static void textRight(PDPageContentStream cs, PDFont font, float size, float right, float y,
                                  String value, float maxWidth) throws Exception {
        String fitted = fitToWidth(font, size, value, maxWidth);
        text(cs, font, size, right - font.getStringWidth(fitted) / 1000f * size, y, fitted);
    }
    private static void fill(PDPageContentStream cs, float[] rgb) throws Exception { cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]); }
    private static void hairline(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        cs.setStrokingColor(HAIRLINE[0], HAIRLINE[1], HAIRLINE[2]); cs.setLineWidth(0.45f);
        cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    private static String repeaterLabel(String value, boolean fr) {
        if (value == null) return "—";
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("oui")) return fr ? "Oui" : "Yes";
        if (v.startsWith("non")) return fr ? "Non" : "No";
        return value;
    }
    private static void textCentered(PDPageContentStream cs, PDFont font, float size, float left, float right,
                                     float y, String value) throws Exception {
        String fitted = fitToWidth(font, size, value, Math.max(0, right - left));
        float width = font.getStringWidth(fitted) / 1000f * size;
        text(cs, font, size, Math.max(left, left + (right - left - width) / 2f), y, fitted);
    }
    /** Clip by rendered width, not character count, so PDF cells can never overprint their neighbour. */
    private static String fitToWidth(PDFont font, float size, String value, float maxWidth) throws Exception {
        String clean = safeText(value);
        if (clean.isBlank() || maxWidth <= 0 || font.getStringWidth(clean) / 1000f * size <= maxWidth) return clean;
        String suffix = "...";
        int end = clean.length();
        while (end > 0) {
            String candidate = clean.substring(0, end).trim() + suffix;
            if (font.getStringWidth(candidate) / 1000f * size <= maxWidth) return candidate;
            end = clean.offsetByCodePoints(end, -1);
        }
        return "";
    }
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
