package com.bbc.sms.documents;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DocumentDesignDtos {
    private DocumentDesignDtos() {}

    public record DocumentDesignView(List<TemplateVersionView> templates,
                                     List<BrandingVersionView> branding,
                                     StandardTemplateProvisioningView provisioning) {
        public DocumentDesignView(List<TemplateVersionView> templates,
                                  List<BrandingVersionView> branding) {
            this(templates, branding, null);
        }
    }

    public record TemplateVersionView(UUID id, String type, String locale, String name,
                                      int version, String templateFamily, String product,
                                      String subsystem, String status, String referenceFamily,
                                      String checksum, Instant publishedAt,
                                      String standardKey, LocalDate effectiveFrom,
                                      LocalDate effectiveTo, String configJson) {}

    public record StandardTemplateFamilyView(String standardKey, String locale, String product,
                                             String family, String label, boolean installed,
                                             int installedVersion, String status,
                                             String effectiveFrom, String effectiveTo) {}

    public record StandardTemplateProvisioningView(boolean hasAnyReportCardTemplate,
                                                   boolean needsInstallation,
                                                   int reportCardTemplateCount,
                                                   List<String> layoutLevels,
                                                   List<StandardTemplateFamilyView> families) {
        public StandardTemplateProvisioningView {
            layoutLevels = layoutLevels == null ? List.of() : List.copyOf(layoutLevels);
            families = families == null ? List.of() : List.copyOf(families);
        }
    }

    public record BrandingVersionView(UUID id, String locale, int version, String status,
                                      String schoolName, String schoolNameEn, String motto,
                                      String ministryText, String city, String country,
                                      String principalName, String principalTitle,
                                      String classMasterTitle, String councilTitle,
                                      String contentHash, Instant createdAt, Instant publishedAt) {}

    public record PublishRequest(@NotBlank String reason, String locale) {}
    public record InstallRequest(@NotBlank String reason) {}
}
