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
}
