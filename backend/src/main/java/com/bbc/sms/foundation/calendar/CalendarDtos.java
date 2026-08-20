package com.bbc.sms.foundation.calendar;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class CalendarDtos {
    private CalendarDtos() {}
    public record CalendarDayView(UUID id, UUID academicSessionId, int dayOfWeek,
                                  boolean teachingDay, LocalTime startTime, LocalTime endTime,
                                  long version) {}
    public record CalendarDayUpdate(int dayOfWeek, boolean teachingDay,
                                    LocalTime startTime, LocalTime endTime, Long version) {}
    public record GenerateRequest(@NotNull UUID academicSessionId, LocalDate startDate,
                                  LocalDate endDate, boolean dryRun) {}
    public record GenerationResult(UUID academicSessionId, LocalDate startDate, LocalDate endDate,
                                   int teachingDates, int classes, int expectedRows,
                                   int existingRows, int insertedRows, int removedFutureRows,
                                   String sourceVersion, boolean dryRun, List<String> warnings) {}
    public record ExpectedSessionView(UUID id, UUID academicSessionId, UUID classId,
                                      LocalDate date, String model, String periodKey,
                                      String sourceVersion, boolean cancelled, String closureReason) {}
}
