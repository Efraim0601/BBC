package com.bbc.sms.foundation.enrollment;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public final class EnrollmentDtos {
    private EnrollmentDtos() {}
    public record EnrollmentView(UUID id, UUID studentId, UUID academicSessionId, String sessionLabel,
                                 UUID classId, String className, String level, String subsystem,
                                 String status, LocalDate enrolledOn, LocalDate exitedOn,
                                 String source, String reason, UUID previousEnrollmentId, long version) {}

    public record EnrollmentRequest(@NotNull UUID academicSessionId, UUID classId,
                                    @NotNull LocalDate enrolledOn, String source, String reason) {}

    public record TransferRequest(UUID academicSessionId, UUID classId,
                                  @NotNull LocalDate effectiveDate, String reason, Long version) {}

    public record WithdrawRequest(@NotNull LocalDate effectiveDate, String reason, Long version) {}
}
