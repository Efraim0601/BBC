package com.bbc.sms.documents;

import com.bbc.sms.academic.security.AcademicAccessPolicyService;
import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.foundation.idempotency.IdempotencyService;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.bbc.sms.documents.OfficialDocumentDtos.*;

@Service
public class OfficialDocumentService {
    private final DocumentTemplateRepository templates;
    private final GeneratedDocumentRepository documents;
    private final DocumentStorage storage;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final AcademicAccessPolicyService academicAccess;

    public OfficialDocumentService(DocumentTemplateRepository templates,
                                   GeneratedDocumentRepository documents,
                                   DocumentStorage storage, IdempotencyService idempotency,
                                   AuditService audit, AcademicAccessPolicyService academicAccess) {
        this.templates = templates; this.documents = documents; this.storage = storage;
        this.idempotency = idempotency; this.audit = audit; this.academicAccess = academicAccess;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        return templates.findBySchoolIdAndActiveTrueOrderByTypeAscLocaleAscTemplateVersionDesc(TenantContext.get())
                .stream().map(t -> new TemplateView(t.getId(), t.getType(), t.getLocale(), t.getName(), t.getTemplateVersion(),
                        t.getTemplateFamily(), t.getProduct(), t.getSubsystem(), t.getStatus(), t.getReferenceFamily(),
                        t.getChecksum(), t.getPublishedAt())).toList();
    }

    @Transactional
    public GeneratedDocumentView generate(GenerateRequest in, String idempotencyKey) {
        return idempotency.execute("official-documents/generate", idempotencyKey, in,
                GeneratedDocumentView.class, () -> generateNow(in));
    }

    /** Register an already-rendered official PDF in the same immutable document ledger. */
    @Transactional
    public GeneratedDocumentView registerPdf(String documentType, String aggregateType, String aggregateId,
                                             String aggregateVersion, String locale, String title, String visibility,
                                             byte[] pdf, String idempotencyKey) {
        return registerPdf(documentType, aggregateType, aggregateId, aggregateVersion, locale, title, visibility,
                pdf, idempotencyKey, null);
    }

    /** Register an official PDF with a caller-owned, atomically allocated number. */
    @Transactional
    public GeneratedDocumentView registerPdf(String documentType, String aggregateType, String aggregateId,
                                             String aggregateVersion, String locale, String title, String visibility,
                                             byte[] pdf, String idempotencyKey, String documentNumber) {
        String normalizedLocale = blank(locale, "fr").toLowerCase(Locale.ROOT);
        PdfRegistration request = new PdfRegistration(documentType, aggregateType, aggregateId,
                blank(aggregateVersion, "1"), normalizedLocale, title, visibility, documentNumber);
        String key = blank(idempotencyKey, "pdf:" + aggregateType + ":" + aggregateId + ":" + request.aggregateVersion() + ":" + normalizedLocale);
        return idempotency.execute("official-documents/pdf", key, request,
                GeneratedDocumentView.class, () -> registerPdfNow(request, pdf));
    }

    private GeneratedDocumentView registerPdfNow(PdfRegistration in, byte[] pdf) {
        UUID schoolId = TenantContext.get();
        GeneratedDocument existing = documents.findFirstBySchoolIdAndDocumentTypeAndAggregateTypeAndAggregateIdAndAggregateVersionAndLocale(
                schoolId, in.documentType(), in.aggregateType(), in.aggregateId(), in.aggregateVersion(), in.locale()).orElse(null);
        if (existing != null) return view(existing);
        UUID id = UUID.randomUUID();
        String normalizedType = in.documentType().trim().toUpperCase(Locale.ROOT);
        String prefix = normalizedType.replaceAll("[^A-Z0-9]", "");
        prefix = prefix.substring(0, Math.min(8, Math.max(1, prefix.length())));
        String number = blank(in.documentNumber(), prefix + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + id.toString().substring(0, 6).toUpperCase(Locale.ROOT));
        GeneratedDocument d = new GeneratedDocument();
        d.setId(id); d.setSchoolId(schoolId); d.setDocumentType(normalizedType);
        d.setAggregateType(in.aggregateType().trim()); d.setAggregateId(in.aggregateId().trim());
        d.setAggregateVersion(in.aggregateVersion()); d.setLocale(in.locale()); d.setDocumentNumber(number);
        d.setTitle(in.title().trim()); d.setStorageKey(storage.store(schoolId.toString(), id.toString(), pdf));
        d.setSha256(sha256(pdf)); d.setSizeBytes(pdf.length); d.setVisibility(normalizeVisibility(in.visibility()));
        d.setSourceEventKey("DOCUMENT:" + in.aggregateType() + ":" + in.aggregateId() + ":" + in.aggregateVersion());
        d.setGeneratedBy(currentUserId()); d.setIssuedAt(Instant.now());
        d = documents.saveAndFlush(d);
        GeneratedDocumentView result = view(d);
        audit.record("DOCUMENT_GENERATED", "GeneratedDocument", id.toString(), null, result, null);
        return result;
    }

    private record PdfRegistration(String documentType, String aggregateType, String aggregateId,
                                   String aggregateVersion, String locale, String title, String visibility,
                                   String documentNumber) {}

    private GeneratedDocumentView generateNow(GenerateRequest in) {
        UUID schoolId = TenantContext.get();
        String locale = blank(in.locale(), "fr").toLowerCase(Locale.ROOT);
        String type = in.documentType().trim().toUpperCase(Locale.ROOT);
        requireAcademicDocumentScope(in.aggregateType(), in.aggregateId());
        DocumentTemplate template = in.templateId() == null
                ? templates.findFirstBySchoolIdAndTypeAndLocaleAndActiveTrueOrderByTemplateVersionDesc(schoolId, type, locale)
                    .orElseGet(() -> templates.findFirstBySchoolIdAndTypeAndLocaleAndActiveTrueOrderByTemplateVersionDesc(schoolId, "GENERIC", locale)
                            .orElseThrow(() -> ApiException.notFound("Modèle de document")))
                : templates.findByIdAndSchoolId(in.templateId(), schoolId).orElseThrow(() -> ApiException.notFound("Modèle de document"));
        String content = render(template.getBodyTemplate(), in.values());
        byte[] pdf = renderPdf(in.title().trim(), content);
        UUID id = UUID.randomUUID();
        String number = type.replaceAll("[^A-Z0-9]", "").substring(0, Math.min(8, type.replaceAll("[^A-Z0-9]", "").length()))
                + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
                + "-" + id.toString().substring(0, 6).toUpperCase(Locale.ROOT);
        GeneratedDocument d = new GeneratedDocument();
        d.setId(id); d.setSchoolId(schoolId); d.setDocumentTemplateId(template.getId()); d.setDocumentType(type);
        d.setAggregateType(in.aggregateType().trim()); d.setAggregateId(in.aggregateId().trim());
        d.setAggregateVersion(blank(in.aggregateVersion(), "1")); d.setLocale(locale); d.setDocumentNumber(number);
        d.setTitle(in.title().trim()); d.setStorageKey(storage.store(schoolId.toString(), id.toString(), pdf));
        d.setSha256(sha256(pdf)); d.setSizeBytes(pdf.length); d.setVisibility(normalizeVisibility(in.visibility()));
        d.setGeneratedBy(currentUserId()); d.setIssuedAt(Instant.now());
        d = documents.saveAndFlush(d);
        GeneratedDocumentView view = view(d);
        audit.record("DOCUMENT_GENERATED", "GeneratedDocument", id.toString(), null, view, null);
        return view;
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentView> list(String aggregateType, String aggregateId) {
        requireAcademicDocumentScope(aggregateType, aggregateId);
        return documents.findBySchoolIdAndAggregateTypeAndAggregateIdOrderByGeneratedAtDesc(
                TenantContext.get(), aggregateType, aggregateId).stream().map(this::view).toList();
    }

    @Transactional
    public byte[] content(UUID id) {
        GeneratedDocument d = find(id);
        requireAcademicDocumentScope(d.getAggregateType(), d.getAggregateId());
        if ("REVOKED".equals(d.getStatus())) throw ApiException.conflict("Ce document a été révoqué");
        byte[] bytes = storage.read(d.getStorageKey());
        if (!sha256(bytes).equals(d.getSha256())) throw new IllegalStateException("Intégrité du document compromise");
        audit.record("DOCUMENT_DOWNLOADED", "GeneratedDocument", id.toString(), null, Map.of("sha256", d.getSha256()), null);
        return bytes;
    }

    @Transactional
    public GeneratedDocumentView revoke(UUID id, RevokeRequest in) {
        GeneratedDocument d = find(id);
        requireAcademicDocumentScope(d.getAggregateType(), d.getAggregateId());
        if ("REVOKED".equals(d.getStatus())) return view(d);
        d.setStatus("REVOKED"); d.setRevokedAt(Instant.now()); d.setRevokedBy(currentUserId()); d.setRevokeReason(in.reason().trim());
        d = documents.saveAndFlush(d);
        audit.record("DOCUMENT_REVOKED", "GeneratedDocument", id.toString(), null, view(d), in.reason());
        return view(d);
    }

    @Transactional
    public GeneratedDocumentView supersede(UUID id, UUID replacementId, String reason) {
        GeneratedDocument d = find(id);
        requireAcademicDocumentScope(d.getAggregateType(), d.getAggregateId());
        if ("SUPERSEDED".equals(d.getStatus())) return view(d);
        GeneratedDocument replacement = find(replacementId);
        requireAcademicDocumentScope(replacement.getAggregateType(), replacement.getAggregateId());
        if (!d.getDocumentType().equals(replacement.getDocumentType())) {
            throw ApiException.badRequest("Le document de remplacement doit être du même type.");
        }
        d.setStatus("SUPERSEDED"); d.setSupersededById(replacement.getId()); d.setSupersededAt(Instant.now());
        d.setSupersededBy(currentUserId()); d.setVoidReason(reason == null ? null : reason.trim());
        d = documents.saveAndFlush(d);
        audit.record("DOCUMENT_SUPERSEDED", "GeneratedDocument", id.toString(), null, view(d), reason);
        return view(d);
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentView byId(UUID id) {
        GeneratedDocument document = find(id);
        requireAcademicDocumentScope(document.getAggregateType(), document.getAggregateId());
        return view(document);
    }

    private void requireAcademicDocumentScope(String aggregateType, String aggregateId) {
        if (!"BulletinVersion".equalsIgnoreCase(aggregateType)) return;
        UUID snapshotId;
        try { snapshotId = UUID.fromString(aggregateId); }
        catch (IllegalArgumentException ex) { throw ApiException.forbidden("Ce document académique n'est pas accessible."); }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && "parent".equalsIgnoreCase(principal.roleCode())) return;
        academicAccess.requireSnapshot(snapshotId,
                AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW);
    }

    /** Public verification deliberately returns no tenant, subject, or storage details. */
    @Transactional(readOnly = true)
    public VerificationView verify(String number) {
        GeneratedDocument d = documents.findFirstByDocumentNumberIgnoreCase(number.trim()).orElse(null);
        if (d == null) return new VerificationView(number, null, null, "NOT_FOUND", null, null, false);
        return new VerificationView(d.getDocumentNumber(), d.getDocumentType(), d.getTitle(), d.getStatus(),
                d.getIssuedAt(), d.getSha256(), "ISSUED".equals(d.getStatus()));
    }

    private GeneratedDocument find(UUID id) { return documents.findByIdAndSchoolId(id, TenantContext.get()).orElseThrow(() -> ApiException.notFound("Document officiel")); }
    private GeneratedDocumentView view(GeneratedDocument d) {
        return new GeneratedDocumentView(d.getId(), d.getDocumentType(), d.getAggregateType(), d.getAggregateId(),
                d.getAggregateVersion(), d.getLocale(), d.getDocumentNumber(), d.getTitle(), d.getSha256(),
                d.getMimeType(), d.getSizeBytes(), d.getStatus(), d.getVisibility(), d.getGeneratedAt(),
                d.getIssuedAt(), d.getRevokedAt(), d.getRevokeReason(), d.getSupersededById(),
                d.getSupersededAt(), d.getVoidReason(), d.getVersion());
    }

    private static String render(String template, Map<String, String> values) {
        String result = template;
        if (values != null) for (var e : values.entrySet()) result = result.replace("{{" + e.getKey() + "}}", blank(e.getValue(), ""));
        return result.replaceAll("\\{\\{[A-Za-z0-9_.-]+}}", "—");
    }

    private static byte[] renderPdf(String title, String content) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<String> lines = wrap(content, 92);
            int at = 0;
            while (at < Math.max(1, lines.size())) {
                PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 16); cs.newLineAtOffset(55, 785);
                    cs.showText(pdfSafe(title)); cs.setFont(PDType1Font.HELVETICA, 10); cs.newLineAtOffset(0, -30);
                    int count = 0;
                    while (at < lines.size() && count++ < 45) { cs.showText(pdfSafe(lines.get(at++))); cs.newLineAtOffset(0, -15); }
                    cs.endText();
                }
                if (lines.isEmpty()) at++;
            }
            doc.save(out); return out.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("Échec de génération PDF", ex); }
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : blank(text, "").split("\\R", -1)) {
            String remaining = paragraph.trim();
            if (remaining.isEmpty()) { out.add(""); continue; }
            while (remaining.length() > width) {
                int cut = remaining.lastIndexOf(' ', width); if (cut < 1) cut = width;
                out.add(remaining.substring(0, cut)); remaining = remaining.substring(cut).trim();
            }
            out.add(remaining);
        }
        return out;
    }

    private static String pdfSafe(String value) { return value.replace('’', '\'').replace('–', '-').replace('—', '-').replace('…', '.'); }
    private static String normalizeVisibility(String value) {
        String v = blank(value, "STAFF").toUpperCase(Locale.ROOT);
        if (!List.of("STAFF", "PARENT", "STUDENT", "PUBLIC").contains(v)) throw ApiException.badRequest("Visibilité de document invalide");
        return v;
    }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.userId() : null;
    }
}
