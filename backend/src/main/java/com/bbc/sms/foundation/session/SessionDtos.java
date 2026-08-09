package com.bbc.sms.foundation.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {
    private SessionDtos() {}

    public record TermView(UUID id, String code, String label, int sequenceNo,
                           LocalDate startDate, LocalDate endDate,
                           Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                           Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                           long version) {}

    public record SessionView(UUID id, String code, String label, LocalDate startDate,
                              LocalDate endDate, String status, boolean current,
                              Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                              Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                              long version, List<TermView> terms) {}

    public record SessionUpsert(@NotBlank String code, @NotBlank String label,
                                @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                String status, Boolean current,
                                Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                Long version) {}

    public record TermUpsert(@NotBlank String code, @NotBlank String label,
                             int sequenceNo, @NotNull LocalDate startDate,
                             @NotNull LocalDate endDate,
                             Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                             Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                             Long version) {}

    public record SessionStateRequest(@NotBlank String status, String reason, Long version) {}

    public record ReportingPeriodView(UUID id, UUID academicSessionId, UUID academicTermId,
                                      String code, String label, String periodType, int displayOrder,
                                      LocalDate startDate, LocalDate endDate,
                                      Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                      Instant reviewOpensAt, Instant reviewClosesAt,
                                      Instant validationOpensAt, Instant validationClosesAt,
                                      Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                      Instant correctionOpensAt, Instant correctionClosesAt,
                                      String calculationPolicy, String status, long version) {}

    public record ReportingPeriodUpsert(@NotBlank String code, @NotBlank String label,
                                        @NotBlank String periodType, UUID academicTermId,
                                        int displayOrder, @NotNull LocalDate startDate,
                                        @NotNull LocalDate endDate,
                                        Instant gradeEntryOpensAt, Instant gradeEntryClosesAt,
                                        Instant reviewOpensAt, Instant reviewClosesAt,
                                        Instant validationOpensAt, Instant validationClosesAt,
                                        Instant bulletinPublishOpensAt, Instant bulletinPublishClosesAt,
                                        Instant correctionOpensAt, Instant correctionClosesAt,
                                        String calculationPolicy, String status, Long version) {}

    public record StandardStructureView(UUID academicSessionId, List<ReportingPeriodView> periods,
                                        List<String> warnings, boolean applied) {}
}
