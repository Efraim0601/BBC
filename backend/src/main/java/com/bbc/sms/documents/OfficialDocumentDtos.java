package com.bbc.sms.documents;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OfficialDocumentDtos {
    private OfficialDocumentDtos() {}
    public record TemplateView(UUID id, String type, String locale, String name, int version,
                               String templateFamily, String product, String subsystem,
                               String status, String referenceFamily, String checksum,
                               Instant publishedAt) {}
    public record GenerateRequest(@NotBlank String documentType, UUID templateId,
                                  @NotBlank String aggregateType, @NotBlank String aggregateId,
                                  String aggregateVersion, String locale, @NotBlank String title,
                                  String visibility, Map<String, String> values) {}
    public record GeneratedDocumentView(UUID id, String documentType, String aggregateType,
                                        String aggregateId, String aggregateVersion, String locale,
                                        String documentNumber, String title, String sha256,
                                        String mimeType, long sizeBytes, String status, String visibility,
                                        Instant generatedAt, Instant issuedAt, Instant revokedAt,
                                        String revokeReason) {}
    public record RevokeRequest(@NotBlank String reason) {}
    public record VerificationView(String documentNumber, String documentType, String title,
                                   String status, Instant issuedAt, String sha256, boolean valid) {}
    public record DocumentList(List<GeneratedDocumentView> documents) {}
}
