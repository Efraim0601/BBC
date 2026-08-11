package com.bbc.sms.academic;

/** Stable, presentation-safe outcomes for official report-card batch work. */
public enum BulletinBatchResultCode {
    REPORT_NOT_CREATED("BUSINESS_BLOCKER", "academic.batch.reportNotCreated", false),
    REPORT_DRAFT("BUSINESS_BLOCKER", "academic.batch.reportDraft", false),
    REPORT_RETURNED("BUSINESS_BLOCKER", "academic.batch.reportReturned", false),
    REPORT_VALIDATED_NOT_PUBLISHED("BUSINESS_BLOCKER", "academic.batch.reportValidatedNotPublished", false),
    REPORT_SUPERSEDED_ONLY("BUSINESS_BLOCKER", "academic.batch.reportSupersededOnly", false),
    REPORT_STALE("BUSINESS_BLOCKER", "academic.batch.reportStale", false),
    REPORT_PUBLICATION_REVOKED("BUSINESS_BLOCKER", "academic.batch.reportPublicationRevoked", false),
    REPORT_PUBLICATION_CHANGED("BUSINESS_BLOCKER", "academic.batch.reportPublicationChanged", false),
    ENROLLMENT_MISSING("BUSINESS_BLOCKER", "academic.batch.enrollmentMissing", false),
    REPORT_NOT_PUBLISHED_LEGACY("BUSINESS_BLOCKER", "academic.batch.reportNotPublishedLegacy", false),
    SNAPSHOT_UNREADABLE("TECHNICAL_ERROR", "academic.batch.snapshotUnreadable", false),
    PDF_RENDER_FAILED("TECHNICAL_ERROR", "academic.batch.pdfRenderFailed", true),
    DOCUMENT_REGISTRATION_FAILED("TECHNICAL_ERROR", "academic.batch.documentRegistrationFailed", true),
    STORAGE_FAILED("TECHNICAL_ERROR", "academic.batch.storageFailed", true),
    UNEXPECTED_GENERATION_ERROR("TECHNICAL_ERROR", "academic.batch.unexpectedGenerationError", true),
    PUBLISHED("SUCCESS", "academic.batch.published", false),
    QUEUED("RUNNING", "academic.batch.queued", false);

    private final String category;
    private final String messageKey;
    private final boolean retryableByDefault;

    BulletinBatchResultCode(String category, String messageKey, boolean retryableByDefault) {
        this.category = category;
        this.messageKey = messageKey;
        this.retryableByDefault = retryableByDefault;
    }

    public String category() { return category; }
    public String messageKey() { return messageKey; }
    public boolean retryableByDefault() { return retryableByDefault; }
    public boolean businessBlocker() { return "BUSINESS_BLOCKER".equals(category); }

    public static BulletinBatchResultCode from(String value) {
        if (value == null || value.isBlank()) return null;
        try { return valueOf(value.trim().toUpperCase()); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
