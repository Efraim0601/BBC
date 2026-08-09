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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

    public ReportCardPdfService(BulletinSnapshotService snapshots, ProfilePhotoRepository photos, SchoolClassRepository classes) {
        this.snapshots = snapshots; this.photos = photos; this.classes = classes;
    }

    public byte[] render(java.util.UUID snapshotId, boolean french) {
        BulletinSnapshotView b = snapshots.byId(snapshotId);
        ProfilePhoto photo = photos.findByOwnerTypeAndOwnerIdAndSchoolId("student", b.studentId(), TenantContext.get()).orElse(null);
        boolean secondary = isSecondary(b);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            NORMAL_FONT.set(loadFont(doc, "/usr/share/fonts/dejavu/DejaVuSans.ttf"));
            BOLD_FONT.set(loadFont(doc, "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"));
            try {
                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                PDImageXObject image = photo == null ? null : PDImageXObject.createFromByteArray(doc, photo.getBytes(), "student-photo");
                float y = header(doc, page, b, french, image, secondary);
                y = tableHeader(doc, page, y, french, secondary);
                for (BulletinLineView line : b.lines()) {
                    if (y < 88) {
                        footer(doc, page, french);
                        page = new PDPage(PDRectangle.A4); doc.addPage(page);
                        y = header(doc, page, b, french, image, secondary);
                        y = tableHeader(doc, page, y, french, secondary);
                    }
                    row(doc, page, y, line, french, secondary);
                    y -= 22;
                }
                y -= 5;
                if (y < 230) { footer(doc, page, french); page = new PDPage(PDRectangle.A4); doc.addPage(page); y = header(doc, page, b, french, image, secondary) - 10; }
                summary(doc, page, y, b, french, secondary);
                signatureBoxes(doc, page, y - 148, french);
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

    private float header(PDDocument doc, PDPage page, BulletinSnapshotView b, boolean fr, PDImageXObject photo, boolean secondary) throws Exception {
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        line(cs, LEFT, 775, RIGHT, 775, 1.2f);
        text(cs, bold(), 10, LEFT, 805, "REPUBLIC OF CAMEROON");
        text(cs, normal(), 8, LEFT, 793, "Peace-Work-Fatherland");
        text(cs, bold(), 10, 310, 805, "RÉPUBLIQUE DU CAMEROUN");
        text(cs, normal(), 8, 310, 793, "Paix-Travail-Patrie");
        text(cs, bold(), 15, 205, 754, "BAYO BILINGUAL COMPLEX");
        text(cs, normal(), 9, 238, 740, "Maroua · Official academic report card");
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

    private float tableHeader(PDDocument doc, PDPage page, float y, boolean fr, boolean secondary) throws Exception {
        if (secondary) return secondaryTableHeader(doc, page, y, fr);
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
            text(cs, bold(), 8, 423, y - 10, fr ? "APPRECIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            for (float x : new float[]{42, 190, 235, 280, 330, 415, 553}) {
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

    private float secondaryTableHeader(PDDocument doc, PDPage page, float y, boolean fr) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(BLUE, 0.34f, 0.57f);
            cs.addRect(LEFT, y - 18, RIGHT - LEFT, 22); cs.fill();
            cs.setNonStrokingColor(1, 1, 1);
            text(cs, bold(), 7, 48, y - 10, fr ? "MATIERE / PROF." : "SUBJECT / TEACHER");
            text(cs, bold(), 7, 150, y - 10, fr ? "EVALUATIONS" : "ASSESSMENTS");
            text(cs, bold(), 7, 256, y - 10, "MOY");
            text(cs, bold(), 7, 303, y - 10, "COEF");
            text(cs, bold(), 7, 350, y - 10, "TOTAL");
            text(cs, bold(), 7, 405, y - 10, fr ? "APPRECIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            for (float x : new float[]{42, 142, 247, 292, 337, 392, 553}) line(cs, x, y + 4, x, y - 18, 0.6f);
            line(cs, LEFT, y - 18, RIGHT, y - 18, 0.8f);
        }
        return y - 18;
    }

    private void secondaryRow(PDDocument doc, PDPage page, float y, BulletinLineView l, boolean fr) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            text(cs, bold(), 7, 48, y - 12, clip(l.subjectLabel(), 16));
            if (l.teacherName() != null && !l.teacherName().isBlank()) text(cs, normal(), 6, 48, y - 20, clip(l.teacherName(), 17));
            text(cs, normal(), 7, 150, y - 14, clip(componentText(l), 18));
            text(cs, normal(), 9, 256, y - 14, number(l.mark()));
            text(cs, normal(), 9, 303, y - 14, String.valueOf(l.coefficient()));
            text(cs, normal(), 9, 350, y - 14, number(l.weighted()));
            String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
            text(cs, normal(), 7, 405, y - 14, clip(remark, 26));
            for (float x : new float[]{42, 142, 247, 292, 337, 392, 553}) line(cs, x, y, x, y - 22, 0.35f);
            line(cs, LEFT, y - 22, RIGHT, y - 22, 0.35f);
        }
    }

    private void row(PDDocument doc, PDPage page, float y, BulletinLineView l, boolean fr, boolean secondary) throws Exception {
        if (secondary) { secondaryRow(doc, page, y, l, fr); return; }
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            String subject = l.subjectGroupLabel() == null || l.subjectGroupLabel().isBlank()
                    ? l.subjectLabel() : l.subjectGroupLabel() + " / " + l.subjectLabel();
            text(cs, bold(), 8, 48, y - 14, clip(subject, 24));
            text(cs, normal(), 9, 198, y - 14, number(l.mark()));
            text(cs, normal(), 9, 247, y - 14, String.valueOf(l.coefficient()));
            text(cs, normal(), 9, 292, y - 14, number(l.weighted()));
            text(cs, normal(), 7, 337, y - 14, clip(componentText(l), 20));
            String remark = l.teacherRemark() == null ? l.appreciation() : l.teacherRemark();
            if (l.teacherName() != null && !l.teacherName().isBlank()) remark = "Prof. " + l.teacherName() + " · " + remark;
            text(cs, normal(), 8, 423, y - 14, clip(remark, 28));
            for (float x : new float[]{42, 190, 235, 280, 330, 415, 553}) {
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
            text(cs, normal(), 8, 430, y - 51, (fr ? "Presence : " : "Attendance: ") + (b.attendance() == null ? "-" : b.attendance().presentCount() + " P / " + b.attendance().absentCount() + " A / " + number(b.attendance().unjustifiedAbsenceHours()) + "h NJ"));
            text(cs, normal(), 8, 52, y - 69, (fr ? "Groupes : " : "Groups: ") + clip(groupText, 76));
            text(cs, normal(), 8, 52, y - 86, (fr ? "Conduite et distinctions : " : "Conduct and awards: ") + clip(conductText, 88));
            text(cs, normal(), 8, 52, y - 103, (fr ? "Decision du conseil : " : "Council decision: ") + clip(decision, 88));
            text(cs, normal(), 8, 52, y - 120, fr ? "Bulletin genere depuis un snapshot academique immuable." : "Generated from an immutable academic snapshot.");
        }
    }

    private void signatureBoxes(PDDocument doc, PDPage page, float top, boolean fr) throws Exception {
        float first = LEFT;
        float width = (RIGHT - LEFT) / 3f;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
            box(cs, first, top - 58, first + width, top);
            box(cs, first + width, top - 58, first + (2 * width), top);
            box(cs, first + (2 * width), top - 58, RIGHT, top);
            text(cs, bold(), 7, first + 8, top - 15, fr ? "VISA DU PARENT" : "PARENT SIGNATURE");
            text(cs, bold(), 7, first + width + 8, top - 15, fr ? "CONSEIL DE CLASSE" : "CLASS COUNCIL");
            text(cs, bold(), 7, first + (2 * width) + 8, top - 15, fr ? "CHEF D'ETABLISSEMENT" : "HEAD OF SCHOOL");
            text(cs, normal(), 7, first + 8, top - 45, fr ? "Signature / date" : "Signature / date");
            text(cs, normal(), 7, first + width + 8, top - 45, fr ? "Avis et visa" : "Decision / signature");
            text(cs, normal(), 7, first + (2 * width) + 8, top - 45, fr ? "Cachet" : "Seal");
        }
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
        return out.toString();
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
