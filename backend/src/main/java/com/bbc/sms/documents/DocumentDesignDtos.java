package com.bbc.sms.documents;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentDesignDtos {
    private DocumentDesignDtos() {}

    public record DocumentDesignView(List<TemplateVersionView> templates,
                                     List<BrandingVersionView> branding) {}

    public record TemplateVersionView(UUID id, String type, String locale, String name,
                                      int version, String templateFamily, String product,
                                      String subsystem, String status, String referenceFamily,
                                      String checksum, Instant publishedAt) {}

    public record BrandingVersionView(UUID id, String locale, int version, String status,
                                      String schoolName, String schoolNameEn, String motto,
                                      String ministryText, String address, String city, String country,
                                      String logoContentType, boolean logoConfigured,
                                      String principalName, String principalTitle,
                                      String classMasterTitle, String councilTitle,
                                      String contentHash, Instant createdAt, Instant publishedAt) {}

    public record PublishRequest(@NotBlank String reason, String locale,
                                 String logoContentType, String logoBase64) {}
}
