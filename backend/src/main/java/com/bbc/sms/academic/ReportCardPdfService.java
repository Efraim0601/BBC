package com.bbc.sms.academic;

import com.bbc.sms.academic.dto.AcademicDtos.*;
import com.bbc.sms.documents.OfficialDocumentDtos.RenderEvidence;
import com.bbc.sms.media.ProfilePhotoRepository;
import com.bbc.sms.timetable.SchoolClassRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic official report-card renderer.
 *
 * <p>The only academic input is the serialized BAY-35 authoritative snapshot
 * returned by {@link BulletinSnapshotService#authoritativeById(UUID)}.  The
 * two asset lookups below are by frozen version ID and tenant ID; the renderer
 * never reads current student, curriculum, teacher, school, attendance, or
 * comment tables.</p>
 */
@Service
public class ReportCardPdfService {
    private static final float LEFT = 42f;
    private static final float RIGHT = 553f;
    private static final float TOP = 805f;
    private static final float BOTTOM = 62f;
    private static final float BLUE_R = 0.20f;
    private static final float BLUE_G = 0.42f;
    private static final float BLUE_B = 0.68f;

    private final BulletinSnapshotService snapshots;
    private final JdbcTemplate jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    public ReportCardPdfService(BulletinSnapshotService snapshots, JdbcTemplate jdbc) {
        this.snapshots = snapshots;
        this.jdbc = jdbc;
    }

    /** Compatibility constructor for callers that still provide legacy media/class repositories. */
    public ReportCardPdfService(BulletinSnapshotService snapshots, ProfilePhotoRepository ignoredPhotos,
                                SchoolClassRepository ignoredClasses, JdbcTemplate jdbc) {
        this(snapshots, jdbc);
    }

    public byte[] render(UUID snapshotId, boolean french) {
        AuthoritativeSnapshotView snapshot = snapshots.authoritativeById(snapshotId);
        if (snapshot == null) throw new IllegalStateException("BAY-35 authoritative snapshot is missing");
        return renderSnapshot(snapshotId, snapshot, french);
    }

    /** Evidence copied to the generated-document ledger beside the PDF bytes. */
    public RenderEvidence evidence(UUID snapshotId) {
        AuthoritativeSnapshotView snapshot = snapshots.authoritativeById(snapshotId);
        if (snapshot == null) throw new IllegalStateException("BAY-35 authoritative snapshot is missing");
        SnapshotTemplateView template = snapshot.template();
        DocumentDesignEvidenceView branding = snapshot.school() == null ? null : snapshot.school().branding();
        if (branding == null && snapshot.evidence() != null) branding = snapshot.evidence().documentDesign();
        String photoHash = snapshot.profilePhoto() == null ? "" : value(snapshot.profilePhoto().sha256());
        String brandingHash = branding == null
                ? template == null ? null : template.brandingHash()
                : branding.brandingHash();
        return new RenderEvidence(template == null ? null : template.templateId(),
                template == null ? null : template.version(), template == null ? null : template.contentHash(),
                template == null ? null : template.brandingId(), template == null ? null : template.brandingVersion(),
                brandingHash, sha256(photoHash + "|" + value(brandingHash)), snapshot.canonicalSnapshotHash());
    }

    /** Stable number printed into the PDF and safe to reuse on retries. */
    public String documentNumber(UUID snapshotId, String locale) {
        String lang = "en".equalsIgnoreCase(locale) ? "EN" : "FR";
        return "BUL-" + lang + "-" + snapshotId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private byte[] renderSnapshot(UUID snapshotId, AuthoritativeSnapshotView snapshot, boolean french) {
        RenderModel model = model(snapshotId, snapshot, french);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont normal = loadFont(document, false);
            PDFont bold = loadFont(document, true);
            fixedMetadata(document, model);

            PDImageXObject photo = imageOrNull(document, model.photoBytes(), "student-photo");
            PDImageXObject logo = imageOrNull(document, model.branding().logoBytes(), "school-logo");
            PDImageXObject stamp = imageOrNull(document, model.branding().stampBytes(), "school-stamp");
            PageState page = newPage(document, model, normal, bold, photo, logo);

            String currentGroup = null;
            for (int index = 0; index < model.subjects().size(); index++) {
                SnapshotSubjectResultView subject = model.subjects().get(index);
                String group = blank(subject.groupCode()) ? null : subject.groupCode();
                boolean groupStarts = group != null && !group.equals(currentGroup);
                float rowHeight = rowHeight(subject, model, normal, bold);
                float required = rowHeight + (groupStarts ? 22f : 0f);
                if (page.y() - required < BOTTOM + 145f) {
                    footer(document, page.page(), model, normal);
                    page = newPage(document, model, normal, bold, photo, logo);
                }
                if (groupStarts) {
                    drawGroupHeader(document, page.page(), page.y(), groupLabel(subject), model, bold);
                    page = new PageState(page.page(), page.y() - 22f);
                    currentGroup = group;
                }
                drawSubjectRow(document, page.page(), page.y(), subject, model, normal, bold, rowHeight);
                page = new PageState(page.page(), page.y() - rowHeight);

                boolean groupEnds = group != null && (index + 1 == model.subjects().size()
                        || !group.equals(value(model.subjects().get(index + 1).groupCode())));
                if (groupEnds) {
                    GroupStatsView stats = model.groups().get(group);
                    if (stats != null) {
                        if (page.y() - 24f < BOTTOM + 145f) {
                            footer(document, page.page(), model, normal);
                            page = newPage(document, model, normal, bold, photo, logo);
                        }
                        drawSubtotal(document, page.page(), page.y(), stats, model, normal, bold);
                        page = new PageState(page.page(), page.y() - 24f);
                    }
                }
            }

            if (page.y() < BOTTOM + 260f) {
                footer(document, page.page(), model, normal);
                page = newPage(document, model, normal, bold, photo, logo);
            }
            drawSummary(document, page.page(), page.y(), model, normal, bold);
            drawSignatures(document, page.page(), page.y() - 170f, model, normal, bold, stamp);
            drawDocumentMetadata(document, page.page(), 75f, model, normal);
            drawQr(document, page.page(), model, 485f, 72f);
            footer(document, page.page(), model, normal);

            document.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate the official report-card PDF", ex);
        }
    }

    private RenderModel model(UUID snapshotId, AuthoritativeSnapshotView snapshot, boolean french) {
        SnapshotSchoolView school = snapshot.school();
        SnapshotEnrollmentView enrollment = snapshot.enrollment();
        SnapshotStudentView student = snapshot.student();
        SnapshotResultView result = snapshot.result() == null
                ? new SnapshotResultView(null, null, null, enrollment == null ? 0 : enrollment.classSize(),
                List.of(), List.of(), null, List.of(), List.of()) : snapshot.result();
        DocumentDesignEvidenceView design = snapshot.evidence() == null ? null : snapshot.evidence().documentDesign();
        if (design == null && school != null) design = school.branding();
        SnapshotTemplateView template = snapshot.template();
        String config = template != null && template.configJson() != null ? template.configJson()
                : design == null ? null : design.templateConfigJson();
        String level = enrollment == null ? "primary" : layoutLevel(enrollment.level());
        String layout = layoutFor(config, level);
        if (layout == null) layout = "secondary".equals(level) ? "SECONDARY" : "maternelle".equals(level) ? "NURSERY" : "PRIMARY";
        Map<String, GroupStatsView> groups = new LinkedHashMap<>();
        for (GroupStatsView group : result.groups() == null ? List.<GroupStatsView>of() : result.groups()) {
            if (group != null && group.code() != null) groups.put(group.code(), group);
        }
        BrandingData branding = branding(snapshot, design);
        return new RenderModel(snapshotId, snapshot, result, result.subjects() == null ? List.of() : result.subjects(),
                groups, components(result.subjects(), modelProduct(snapshot)), french,
                "ANNUAL".equalsIgnoreCase(modelProduct(snapshot)), layout, branding,
                snapshotPhoto(snapshot), school == null ? null : school.id());
    }

    private String modelProduct(AuthoritativeSnapshotView snapshot) {
        return snapshot.product() == null ? "TERM" : snapshot.product();
    }

    private String layoutLevel(String raw) {
        return switch (value(raw).trim().toLowerCase(Locale.ROOT)) {
            case "maternelle", "nursery", "kindergarten" -> "maternelle";
            case "secondary", "secondaire" -> "secondary";
            default -> "primary";
        };
    }

    private List<String> components(List<SnapshotSubjectResultView> subjects, String product) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (SnapshotSubjectResultView subject : subjects == null ? List.<SnapshotSubjectResultView>of() : subjects) {
            if (subject == null) continue;
            if (subject.components() != null) {
                for (PeriodMarkView mark : subject.components()) if (mark != null && !blank(mark.periodCode())) values.add(mark.periodCode());
            }
            if (values.isEmpty() && subject.assessments() != null) {
                for (AssessmentEvidenceView assessment : subject.assessments()) {
                    if (assessment != null && !blank(assessment.code())) values.add(assessment.code());
                }
            }
        }
        if (values.isEmpty()) values.add("ANNUAL".equalsIgnoreCase(product) ? "RESULT" : "MARK");
        return List.copyOf(values);
    }

    private PageState newPage(PDDocument document, RenderModel model, PDFont normal, PDFont bold,
                              PDImageXObject photo, PDImageXObject logo) throws Exception {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        float y = header(document, page, model, normal, bold, photo, logo);
        y = tableHeader(document, page, y, model, normal, bold);
        return new PageState(page, y);
    }

    private float header(PDDocument document, PDPage page, RenderModel model, PDFont normal, PDFont bold,
                         PDImageXObject photo, PDImageXObject logo) throws Exception {
        AuthoritativeSnapshotView s = model.snapshot();
        SnapshotSchoolView school = s.school();
        SnapshotStudentView student = s.student();
        SnapshotEnrollmentView enrollment = s.enrollment();
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            line(cs, LEFT, 777, RIGHT, 777, 1.1f);
            text(cs, bold, 9, LEFT, 807, "REPUBLIC OF CAMEROON");
            text(cs, normal, 7, LEFT, 795, "Peace - Work - Fatherland");
            text(cs, bold, 9, 318, 807, "R\u00c9PUBLIQUE DU CAMEROUN");
            text(cs, normal, 7, 318, 795, "Paix - Travail - Patrie");
            if (logo != null) cs.drawImage(logo, 48, 735, 38, 38);

            String schoolName = model.branding().schoolName(model.french());
            drawCentered(cs, bold, 14, schoolName, 300, 752, 285);
            String location = join(value(school == null ? null : school.city()), value(school == null ? null : school.country()), "");
            String authority = firstNonBlank(model.branding().ministryText(), school == null ? null : school.authority());
            if (!blank(authority)) drawCentered(cs, normal, 7, authority, 300, 738, 350);
            if (!blank(location)) drawCentered(cs, normal, 8, location, 300, 727, 350);
            line(cs, LEFT, 716, RIGHT, 716, 0.7f);

            String title = model.french() ? "BULLETIN SCOLAIRE" : "SCHOOL REPORT CARD";
            drawCentered(cs, bold, 12, title + " - " + value(s.reportingPeriodLabel()), 300, 696, 475);
            text(cs, bold, 8, LEFT, 674, model.french() ? "IDENTIT\u00c9 DE L'\u00c9L\u00c8VE" : "STUDENT IDENTITY");
            box(cs, LEFT, 596, RIGHT, 666);
            text(cs, bold, 9, LEFT + 10, 650, value(student == null ? null : student.name()));
            text(cs, normal, 8, LEFT + 10, 634, (model.french() ? "Matricule : " : "Student ID: ") + value(student == null ? null : student.matricule()));
            text(cs, normal, 8, LEFT + 10, 618, (model.french() ? "Classe : " : "Class: ") + value(enrollment == null ? null : enrollment.classLabel()));
            text(cs, normal, 8, 285, 634, (model.french() ? "Effectif : " : "Class size: ") + (enrollment == null ? 0 : enrollment.classSize()));
            String master = s.staff() == null || s.staff().classMaster() == null ? "" : teacherProvenance(s.staff().classMaster());
            text(cs, normal, 8, 285, 618, (model.french() ? "Professeur principal : " : "Class master: ") + value(master));
            if (photo != null) cs.drawImage(photo, 472, 606, 62, 62);
            else {
                box(cs, 472, 606, 534, 662);
                drawCentered(cs, bold, 10, initials(student == null ? null : student.name()), 503, 628, 48);
            }
            String layoutLabel = switch (model.layout()) {
                case "NURSERY" -> model.french() ? "MISE EN PAGE MATERNELLE" : "NURSERY LAYOUT";
                case "SECONDARY" -> model.french() ? "MISE EN PAGE SECONDAIRE" : "SECONDARY LAYOUT";
                default -> model.french() ? "MISE EN PAGE PRIMAIRE" : "PRIMARY LAYOUT";
            };
            text(cs, bold, 7, LEFT + 10, 584, layoutLabel);
        }
        return 562;
    }

    private float tableHeader(PDDocument document, PDPage page, float y, RenderModel model,
                              PDFont normal, PDFont bold) throws Exception {
        ColumnLayout columns = columns(model);
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(BLUE_R, BLUE_G, BLUE_B);
            cs.addRect(LEFT, y - 20, RIGHT - LEFT, 24);
            cs.fill();
            cs.setNonStrokingColor(1, 1, 1);
            text(cs, bold, 7, LEFT + 5, y - 11,
                    "SECONDARY".equals(model.layout()) ? (model.french() ? "MATI\u00c8RE / PROF." : "SUBJECT / TEACHER")
                            : (model.french() ? "MATI\u00c8RE / ENSEIGNANT" : "SUBJECT / TEACHER"));
            for (int i = 0; i < columns.components().size(); i++) {
                text(cs, bold, 6, columns.componentLeft(i) + 3, y - 11, compact(columns.components().get(i), 9));
            }
            String currentLabel = "SEQUENCE".equalsIgnoreCase(modelProduct(model.snapshot()))
                    ? (model.french() ? "SÉQUENCE" : "SEQUENCE")
                    : model.annual() ? (model.french() ? "ANNUEL" : "ANNUAL")
                    : (model.french() ? "TERME" : "TERM");
            text(cs, bold, 6, columns.currentLeft() + 3, y - 11, currentLabel);
            text(cs, bold, 6, columns.coefficientLeft() + 3, y - 11, "COEF");
            text(cs, bold, 6, columns.weightedLeft() + 3, y - 11, model.french() ? "TOTAL POND." : "WEIGHTED");
            text(cs, bold, 6, columns.rankLeft() + 3, y - 11, model.french() ? "RANG" : "RANK");
            text(cs, bold, 6, columns.teacherLeft() + 3, y - 11, model.french() ? "PROF." : "TEACHER");
            text(cs, bold, 6, columns.remarkLeft() + 3, y - 11, model.french() ? "APPR\u00c9CIATION" : "REMARK");
            cs.setNonStrokingColor(0, 0, 0);
            for (float x : columns.boundaries()) line(cs, x, y + 4, x, y - 20, 0.5f);
            line(cs, LEFT, y - 20, RIGHT, y - 20, 0.7f);
        }
        return y - 20;
    }

    private void drawGroupHeader(PDDocument document, PDPage page, float y, String label,
                                 RenderModel model, PDFont bold) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(0.90f, 0.94f, 0.98f);
            cs.addRect(LEFT, y - 21, RIGHT - LEFT, 21);
            cs.fill();
            cs.setNonStrokingColor(0.10f, 0.20f, 0.30f);
            text(cs, bold, 7, LEFT + 6, y - 13,
                    (model.french() ? "GROUPE : " : "GROUP: ") + value(label));
            line(cs, LEFT, y - 21, RIGHT, y - 21, 0.35f);
        }
    }

    private void drawSubjectRow(PDDocument document, PDPage page, float y, SnapshotSubjectResultView subject,
                                RenderModel model, PDFont normal, PDFont bold, float height) throws Exception {
        ColumnLayout columns = columns(model);
        String teacher = teacherName(model.snapshot(), subject.subjectCode());
        String remark = firstNonBlank(subject.teacherRemark(), subject.appreciation());
        List<String> subjectLines = wrap(value(subject.subjectLabel()), bold, 7, columns.subjectWidth() - 8);
        List<String> teacherLines = wrap(teacher, normal, 6, columns.subjectWidth() - 8);
        List<String> remarkLines = wrap(remark, normal, 6, columns.remarkWidth() - 8);
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            drawLines(cs, subjectLines, bold, 7, LEFT + 5, y - 11, 8);
            drawLines(cs, teacherLines, normal, 6, LEFT + 5, y - 11 - subjectLines.size() * 8, 7);
            for (int i = 0; i < columns.components().size(); i++) {
                String code = columns.components().get(i);
                BigDecimal mark = componentMark(subject, code);
                drawCentered(cs, normal, 7, number(mark), columns.componentLeft(i) + columns.componentWidth() / 2f,
                        y - 14, columns.componentWidth() - 4);
            }
            drawCentered(cs, bold, 8, number(subject.displayValue()), columns.currentLeft() + columns.currentWidth() / 2f, y - 14, columns.currentWidth() - 4);
            drawCentered(cs, normal, 7, String.valueOf(subject.coefficient()), columns.coefficientLeft() + columns.coefficientWidth() / 2f, y - 14, columns.coefficientWidth() - 4);
            drawCentered(cs, normal, 7, number(subject.displayWeighted()), columns.weightedLeft() + columns.weightedWidth() / 2f, y - 14, columns.weightedWidth() - 4);
            drawCentered(cs, normal, 7, subject.subjectRank() == null ? "\u2014" : String.valueOf(subject.subjectRank()), columns.rankLeft() + columns.rankWidth() / 2f, y - 14, columns.rankWidth() - 4);
            drawLines(cs, wrap(teacher, normal, 6, columns.teacherWidth() - 8), normal, 6, columns.teacherLeft() + 4, y - 12, 7);
            drawLines(cs, remarkLines, normal, 6, columns.remarkLeft() + 4, y - 12, 7);
            for (float x : columns.boundaries()) line(cs, x, y, x, y - height, 0.3f);
            line(cs, LEFT, y - height, RIGHT, y - height, 0.3f);
        }
    }

    private void drawSubtotal(PDDocument document, PDPage page, float y, GroupStatsView stats,
                              RenderModel model, PDFont normal, PDFont bold) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            cs.setNonStrokingColor(0.96f, 0.97f, 0.99f);
            cs.addRect(LEFT, y - 23, RIGHT - LEFT, 23);
            cs.fill();
            cs.setNonStrokingColor(0.10f, 0.20f, 0.30f);
            text(cs, bold, 7, LEFT + 5, y - 15,
                    (model.french() ? "SOUS-TOTAL " : "SUBTOTAL ") + value(stats.label()) +
                            "  " + (model.french() ? "Moyenne : " : "Average: ") + number(stats.average()) +
                            "  " + (model.french() ? "Total pondéré : " : "Weighted total: ") + number(stats.total()) +
                            "  " + (model.french() ? "Coef : " : "Coef: ") + stats.coefficient());
            line(cs, LEFT, y - 23, RIGHT, y - 23, 0.35f);
        }
    }

    private void drawSummary(PDDocument document, PDPage page, float y, RenderModel model,
                             PDFont normal, PDFont bold) throws Exception {
        AuthoritativeSnapshotView s = model.snapshot();
        SnapshotResultView result = model.result();
        ClassStatsView stats = result.classStats();
        AttendanceSummaryView attendance = s.attendance();
        ConductSummaryView conduct = s.conduct();
        float bottom = y - 145f;
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            box(cs, LEFT, bottom, RIGHT, y);
            text(cs, bold, 8, LEFT + 8, y - 15, model.french() ? "R\u00c9CAPITULATIF DU BULLETIN" : "REPORT-CARD RECAP");
            text(cs, normal, 7, LEFT + 8, y - 32, (model.french() ? "Moyenne de l'élève : " : "Student average: ") + number(result.displayAverage()) + " / 20");
            text(cs, normal, 7, LEFT + 8, y - 47, (model.french() ? "Rang : " : "Rank: ") + (result.overallRank() == null ? "\u2014" : result.overallRank()) + " / " + result.classSize());
            text(cs, normal, 7, 195, y - 32, (model.french() ? "Moyenne classe : " : "Class average: ") + number(stats == null ? null : stats.average()));
            text(cs, normal, 7, 195, y - 47, (model.french() ? "Min / max : " : "Min / max: ") + (stats == null ? "\u2014" : number(stats.minimum()) + " / " + number(stats.maximum())));
            text(cs, normal, 7, 355, y - 32, (model.french() ? "Taux de réussite : " : "Pass rate: ") + (stats == null ? "\u2014" : number(stats.successRate()) + "%"));
            text(cs, normal, 7, 355, y - 47, (model.french() ? "Élèves classés : " : "Ranked students: ") + (stats == null ? "\u2014" : stats.rankedCount()));
            String attendanceText = attendance == null ? "\u2014" : attendance.presentCount() + " P / " + attendance.absentCount() + " A / "
                    + attendance.excusedCount() + " E / " + attendance.lateCount() + " L / " + number(attendance.unjustifiedAbsenceHours()) + "h";
            text(cs, normal, 7, LEFT + 8, y - 66, (model.french() ? "Présence : " : "Attendance: ") + attendanceText);
            String conductText = conductText(conduct, model.french());
            text(cs, normal, 7, LEFT + 8, y - 82, (model.french() ? "Conduite / distinctions : " : "Conduct / honors: ") + conductText);
            String decision = conduct == null ? "\u2014" : firstNonBlank(conduct.decisionCode(), "\u2014");
            String observation = conduct == null ? "" : conduct.councilObservation();
            text(cs, normal, 7, LEFT + 8, y - 98, (model.french() ? "Conseil : " : "Council: ") + decision + (blank(observation) ? "" : " / " + observation));
            String recap = trimesterRecap(model);
            text(cs, normal, 7, LEFT + 8, y - 114, (model.french() ? "Récapitulatif des périodes : " : "Term recap: ") + recap);
            text(cs, normal, 6, LEFT + 8, y - 132,
                    model.french() ? "Document produit exclusivement depuis le snapshot académique immuable."
                            : "Document rendered exclusively from the immutable academic snapshot.");
        }
    }

    private void drawSignatures(PDDocument document, PDPage page, float top, RenderModel model,
                                PDFont normal, PDFont bold, PDImageXObject stamp) throws Exception {
        float width = (RIGHT - LEFT) / 3f;
        String master = model.snapshot().staff() == null || model.snapshot().staff().classMaster() == null
                ? "" : teacherProvenance(model.snapshot().staff().classMaster());
        String principal = firstNonBlank(model.branding().principalName(), "");
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            for (int i = 0; i < 3; i++) box(cs, LEFT + i * width, top - 58, LEFT + (i + 1) * width, top);
            text(cs, bold, 6, LEFT + 7, top - 14, model.french() ? "VISA DU PARENT" : "PARENT SIGNATURE");
            text(cs, bold, 6, LEFT + width + 7, top - 14, firstNonBlank(model.branding().classMasterTitle(), model.french() ? "CONSEIL DE CLASSE" : "CLASS COUNCIL"));
            text(cs, bold, 6, LEFT + 2 * width + 7, top - 14, firstNonBlank(model.branding().principalTitle(), model.french() ? "CHEF D'\u00c9TABLISSEMENT" : "HEAD OF SCHOOL"));
            text(cs, normal, 6, LEFT + 7, top - 43, model.french() ? "Signature / date" : "Signature / date");
            text(cs, normal, 6, LEFT + width + 7, top - 31, wrapFirst(master, normal, 6, width - 14));
            text(cs, normal, 6, LEFT + 2 * width + 7, top - 31, wrapFirst(principal, normal, 6, width - 14));
            if (stamp != null) cs.drawImage(stamp, LEFT + 2 * width + width - 43, top - 55, 30, 30);
        }
    }

    private void drawDocumentMetadata(PDDocument document, PDPage page, float y, RenderModel model, PDFont normal) throws Exception {
        AuthoritativeSnapshotView s = model.snapshot();
        SnapshotTemplateView template = s.template();
        DocumentDesignEvidenceView design = s.school() == null ? null : s.school().branding();
        if (design == null && s.evidence() != null) design = s.evidence().documentDesign();
        String templateText = template == null ? "\u2014" : value(template.templateFamily()) + " v" + template.version() + " / " + shortHash(template.contentHash());
        String brandingText = template == null || template.brandingId() == null ? "\u2014" : "v" + template.brandingVersion() + " / " + shortHash(design == null ? null : design.brandingHash());
        textAt(document, page, normal, 6, LEFT, y, (model.french() ? "N° document : " : "Document no.: ") + documentNumber(model.snapshotId(), model.french() ? "fr" : "en")
                + "   " + (model.french() ? "Version snapshot : " : "Snapshot version: ") + s.contractVersion()
                + "   " + (model.french() ? "Hash : " : "Hash: ") + shortHash(s.canonicalSnapshotHash()));
        textAt(document, page, normal, 6, LEFT, y - 10,
                (model.french() ? "Modèle : " : "Template: ") + templateText + "   " + (model.french() ? "Identité : " : "Branding: ") + brandingText);
    }

    private void drawQr(PDDocument document, PDPage page, RenderModel model, float x, float y) {
        String hash = value(model.snapshot().canonicalSnapshotHash());
        if (hash.isBlank()) return;
        try {
            String payload = "/api/public/report-card-verification/" + model.snapshotId() + "?checksum=" + hash;
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 96, 96);
            BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);
            PDImageXObject image = LosslessFactory.createFromImage(document, qr);
            try (PDPageContentStream cs = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                cs.drawImage(image, x, y, 56, 56);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to render the verification QR", ex);
        }
    }

    private void footer(PDDocument document, PDPage page, RenderModel model, PDFont normal) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            line(cs, LEFT, 50, RIGHT, 50, 0.5f);
            text(cs, normal, 6, LEFT, 37,
                    model.french() ? "Document officiel - conserver sans rature."
                            : "Official document - retain without alteration.");
            text(cs, normal, 6, 390, 37, model.french() ? "Bayo Bilingual Complex" : "Bayo Bilingual Complex");
        }
    }

    private ColumnLayout columns(RenderModel model) {
        int count = Math.min(5, Math.max(1, model.components().size()));
        List<String> components = model.components().subList(0, count);
        float subjectRight = 145f;
        float fixed = 36f + 54f + 32f + 70f + 100f;
        float componentWidth = Math.max(20f, Math.min(36f, (RIGHT - subjectRight - fixed) / count));
        float currentLeft = subjectRight + componentWidth * count;
        float coefficientLeft = currentLeft + 36f;
        float weightedLeft = coefficientLeft + 54f;
        float rankLeft = weightedLeft + 32f;
        float teacherLeft = rankLeft + 70f;
        float remarkLeft = teacherLeft + 100f;
        if (remarkLeft > RIGHT) {
            float overflow = remarkLeft - RIGHT;
            teacherLeft -= overflow;
            rankLeft -= overflow;
            weightedLeft -= overflow;
            coefficientLeft -= overflow;
            currentLeft -= overflow;
            componentWidth = Math.max(18f, (currentLeft - subjectRight) / count);
        }
        return new ColumnLayout(components, subjectRight, componentWidth, currentLeft, coefficientLeft,
                weightedLeft, rankLeft, teacherLeft, remarkLeft);
    }

    private float rowHeight(SnapshotSubjectResultView subject, RenderModel model, PDFont normal, PDFont bold) throws Exception {
        ColumnLayout c = columns(model);
        String teacher = teacherName(model.snapshot(), subject.subjectCode());
        String remark = firstNonBlank(subject.teacherRemark(), subject.appreciation());
        int subjectLines = wrap(value(subject.subjectLabel()), bold, 7, c.subjectWidth() - 8).size();
        int teacherLines = wrap(teacher, normal, 6, c.subjectWidth() - 8).size();
        int remarkLines = wrap(remark, normal, 6, c.remarkWidth() - 8).size();
        return Math.max(24f, 8f + Math.max(subjectLines + teacherLines, remarkLines) * 8f);
    }

    private BigDecimal componentMark(SnapshotSubjectResultView subject, String code) {
        if (subject.components() != null) {
            for (PeriodMarkView mark : subject.components()) if (mark != null && code.equals(mark.periodCode())) return mark.mark();
        }
        if (subject.assessments() != null) {
            for (AssessmentEvidenceView assessment : subject.assessments()) if (assessment != null && code.equals(assessment.code())) return assessment.mark();
        }
        return null;
    }

    private String teacherName(AuthoritativeSnapshotView snapshot, String subjectCode) {
        if (snapshot.staff() == null || snapshot.staff().subjectTeachers() == null) return "";
        return snapshot.staff().subjectTeachers().stream()
                .filter(teacher -> teacher != null && value(subjectCode).equalsIgnoreCase(value(teacher.subjectCode())))
                .map(this::teacherProvenance).filter(name -> !blank(name)).findFirst().orElse("");
    }

    private String teacherProvenance(SnapshotTeacherView teacher) {
        if (teacher == null || blank(teacher.name())) return "";
        return teacher.assignmentVersion() > 0
                ? value(teacher.name()) + " (v" + teacher.assignmentVersion() + ")"
                : value(teacher.name());
    }

    private String groupLabel(SnapshotSubjectResultView subject) {
        return firstNonBlank(subject.groupLabel(), subject.groupCode());
    }

    private String conductText(ConductSummaryView conduct, boolean french) {
        if (conduct == null) return "\u2014";
        List<String> labels = new ArrayList<>();
        if (conduct.honorRoll()) labels.add(french ? "Tableau d'honneur" : "Honor roll");
        if (conduct.encouragement()) labels.add(french ? "Encouragement" : "Encouragement");
        if (conduct.congratulations()) labels.add(french ? "Félicitations" : "Congratulations");
        if (conduct.workWarning()) labels.add(french ? "Avertissement travail" : "Work warning");
        if (conduct.workBlame()) labels.add(french ? "Blâme travail" : "Work blame");
        if (conduct.conductWarning()) labels.add(french ? "Avertissement conduite" : "Conduct warning");
        if (conduct.conductBlame()) labels.add(french ? "Blâme conduite" : "Conduct blame");
        return labels.isEmpty() ? (french ? "Aucune distinction" : "No distinction") : String.join(" / ", labels);
    }

    private String trimesterRecap(RenderModel model) {
        if (!model.annual()) return value(model.snapshot().reportingPeriodLabel());
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String component : model.components()) if (component != null) values.add(compact(component, 12));
        return values.isEmpty() ? "\u2014" : String.join(" / ", values);
    }

    private BrandingData branding(AuthoritativeSnapshotView snapshot, DocumentDesignEvidenceView design) {
        SnapshotSchoolView school = snapshot.school();
        UUID brandingId = snapshot.template() == null ? null : snapshot.template().brandingId();
        if (brandingId == null && design != null) brandingId = design.brandingId();
        BrandingData fallback = new BrandingData(school == null ? "" : school.name(), "", "",
                school == null ? "" : school.authority(), "", school == null ? "" : school.city(),
                school == null ? "" : school.country(), school == null ? "" : school.address(), null, null,
                design == null ? "" : design.principalName(), design == null ? "" : design.principalTitle(),
                design == null ? "" : design.classMasterTitle(), design == null ? "" : design.councilTitle());
        if (brandingId == null || school == null || school.id() == null) return fallback;
        return jdbc.query("""
                SELECT school_name,school_name_en,motto,ministry_text,delegation_text,city,country,address,
                       logo_bytes,stamp_bytes,principal_name,principal_title,class_master_title,council_title
                  FROM document_branding_version
                 WHERE id=? AND school_id=?
                """, rs -> rs.next() ? new BrandingData(rs.getString("school_name"), rs.getString("school_name_en"),
                rs.getString("motto"), rs.getString("ministry_text"), rs.getString("delegation_text"),
                rs.getString("city"), rs.getString("country"), rs.getString("address"), rs.getBytes("logo_bytes"),
                rs.getBytes("stamp_bytes"), rs.getString("principal_name"), rs.getString("principal_title"),
                rs.getString("class_master_title"), rs.getString("council_title")) : fallback,
                brandingId, school.id());
    }

    private byte[] snapshotPhoto(AuthoritativeSnapshotView snapshot) {
        if (snapshot.profilePhoto() == null || snapshot.profilePhoto().assetVersionId() == null || snapshot.school() == null) return null;
        return jdbc.query("SELECT bytes FROM profile_photo_version WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getBytes(1) : null,
                snapshot.profilePhoto().assetVersionId(), snapshot.schoolId());
    }

    private static PDImageXObject imageOrNull(PDDocument document, byte[] bytes, String name) {
        if (bytes == null || bytes.length == 0) return null;
        try { return PDImageXObject.createFromByteArray(document, bytes, name); }
        catch (Exception ex) { return null; }
    }

    private PDFont loadFont(PDDocument document, boolean bold) throws Exception {
        String resource = bold ? "/fonts/DejaVuSans-Bold.ttf" : "/fonts/DejaVuSans.ttf";
        InputStream stream = ReportCardPdfService.class.getResourceAsStream(resource);
        if (stream != null) return PDType0Font.load(document, stream, true);
        String[] candidates = bold
                ? new String[]{"/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "C:\\Windows\\Fonts\\arialbd.ttf"}
                : new String[]{"/usr/share/fonts/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "C:\\Windows\\Fonts\\arial.ttf"};
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile()) return PDType0Font.load(document, file);
        }
        throw new IllegalStateException("No embedded Unicode-capable PDF font is available");
    }

    private void fixedMetadata(PDDocument document, RenderModel model) {
        document.setDocumentId(0L);
        Calendar fixed = new GregorianCalendar(java.util.TimeZone.getTimeZone(ZoneOffset.UTC));
        fixed.setTimeInMillis(0L);
        document.getDocumentInformation().setCreationDate(fixed);
        document.getDocumentInformation().setModificationDate(fixed);
        document.getDocumentInformation().setProducer("BBC SMS BAY-36");
        document.getDocumentInformation().setCreator("BBC SMS");
        document.getDocumentInformation().setTitle(documentNumber(model.snapshotId(), model.french() ? "fr" : "en"));
    }

    private static void textAt(PDDocument document, PDPage page, PDFont font, float size, float x, float y, String value) throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            text(cs, font, size, x, y, value);
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(oneLine(value));
        cs.endText();
    }

    private static void drawCentered(PDPageContentStream cs, PDFont font, float size, String value,
                                     float center, float y, float maxWidth) throws Exception {
        String line = oneLine(value);
        float width = font.getStringWidth(line) / 1000f * size;
        if (width > maxWidth) line = compact(line, Math.max(8, (int) (maxWidth / Math.max(1f, size * 0.48f))));
        width = font.getStringWidth(line) / 1000f * size;
        text(cs, font, size, center - width / 2f, y, line);
    }

    private static void drawLines(PDPageContentStream cs, List<String> lines, PDFont font, float size,
                                  float x, float y, float leading) throws Exception {
        for (int i = 0; i < lines.size(); i++) text(cs, font, size, x, y - i * leading, lines.get(i));
    }

    private static List<String> wrap(String value, PDFont font, float size, float maxWidth) throws Exception {
        String clean = textValue(value);
        if (clean.isBlank()) return List.of("");
        List<String> result = new ArrayList<>();
        for (String paragraph : clean.split("\\R", -1)) {
            String remaining = paragraph.trim();
            if (remaining.isEmpty()) { result.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : remaining.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000f * size <= maxWidth || line.isEmpty()) {
                    if (line.isEmpty() && font.getStringWidth(word) / 1000f * size > maxWidth) {
                        splitLongWord(result, word, font, size, maxWidth);
                    } else {
                        line.setLength(0);
                        line.append(candidate);
                    }
                } else {
                    result.add(line.toString());
                    line.setLength(0);
                    if (font.getStringWidth(word) / 1000f * size > maxWidth) splitLongWord(result, word, font, size, maxWidth);
                    else line.append(word);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static void splitLongWord(List<String> result, String word, PDFont font, float size, float maxWidth) throws Exception {
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < word.length(); ) {
            int next = word.offsetByCodePoints(i, 1);
            String candidate = part + word.substring(i, next);
            if (!part.isEmpty() && font.getStringWidth(candidate) / 1000f * size > maxWidth) {
                result.add(part.toString());
                part.setLength(0);
            }
            part.append(word, i, next);
            i = next;
        }
        if (!part.isEmpty()) result.add(part.toString());
    }

    private static String wrapFirst(String value, PDFont font, float size, float width) throws Exception {
        return wrap(value, font, size, width).getFirst();
    }

    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2, float width) throws Exception {
        cs.setStrokingColor(0.15f, 0.20f, 0.25f);
        cs.setLineWidth(width);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static void box(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        line(cs, x1, y1, x2, y1, 0.5f);
        line(cs, x1, y2, x2, y2, 0.5f);
        line(cs, x1, y1, x1, y2, 0.5f);
        line(cs, x2, y1, x2, y2, 0.5f);
    }

    private static String layoutFor(String config, String level) {
        if (config == null) return null;
        String needle = "\"" + level + "\":\"";
        int start = config.indexOf(needle);
        if (start < 0) return null;
        start += needle.length();
        int end = config.indexOf('"', start);
        return end < 0 ? null : config.substring(start, end).toUpperCase(Locale.ROOT);
    }

    private static String number(BigDecimal value) {
        return value == null ? "\u2014" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String initials(String value) {
        String[] parts = oneLine(value).split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return "?";
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String compact(String value, int max) {
        String text = oneLine(value);
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 1)) + "\u2026";
    }

    private static String shortHash(String value) { return blank(value) ? "\u2014" : value.substring(0, Math.min(12, value.length())); }

    private static String join(String left, String right, String fallback) {
        String result = blank(left) ? value(right) : blank(right) ? left : left + ", " + right;
        return blank(result) ? fallback : result;
    }

    private static String firstNonBlank(String first, String second) { return blank(first) ? value(second) : first; }
    private static String value(String text) { return text == null ? "" : text; }
    private static boolean blank(String text) { return text == null || text.isBlank(); }

    private static String oneLine(String value) {
        return textValue(value).replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
    }

    /** Unicode is intentionally preserved; only control characters are removed. */
    private static String textValue(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().filter(cp -> cp == '\n' || cp == '\r' || cp == '\t' || !Character.isISOControl(cp))
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private record PageState(PDPage page, float y) {}

    private record ColumnLayout(List<String> components, float subjectRight, float componentWidth,
                                float currentLeft, float coefficientLeft, float weightedLeft,
                                float rankLeft, float teacherLeft, float remarkLeft) {
        float subjectWidth() { return subjectRight - LEFT; }
        float currentWidth() { return coefficientLeft - currentLeft; }
        float coefficientWidth() { return weightedLeft - coefficientLeft; }
        float weightedWidth() { return rankLeft - weightedLeft; }
        float rankWidth() { return teacherLeft - rankLeft; }
        float teacherWidth() { return remarkLeft - teacherLeft; }
        float remarkWidth() { return RIGHT - remarkLeft; }
        float componentLeft(int index) { return subjectRight + index * componentWidth; }
        List<Float> boundaries() {
            List<Float> values = new ArrayList<>();
            values.add(LEFT); values.add(subjectRight);
            for (int i = 0; i < components.size(); i++) values.add(componentLeft(i) + componentWidth);
            values.add(coefficientLeft); values.add(weightedLeft); values.add(rankLeft); values.add(teacherLeft); values.add(remarkLeft); values.add(RIGHT);
            return values;
        }
    }

    private record RenderModel(UUID snapshotId, AuthoritativeSnapshotView snapshot,
                               SnapshotResultView result,
                               List<SnapshotSubjectResultView> subjects,
                               Map<String, GroupStatsView> groups, List<String> components,
                               boolean french, boolean annual, String layout,
                               BrandingData branding, byte[] photoBytes, UUID schoolId) {
    }

    private record BrandingData(String schoolName, String schoolNameEn, String motto, String ministryText,
                                String delegationText, String city, String country, String address,
                                byte[] logoBytes, byte[] stampBytes, String principalName,
                                String principalTitle, String classMasterTitle, String councilTitle) {
        String schoolName(boolean french) {
            return french || blank(schoolNameEn) ? value(schoolName) : schoolNameEn;
        }
    }
}
