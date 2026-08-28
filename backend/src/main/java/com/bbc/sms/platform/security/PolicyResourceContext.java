package com.bbc.sms.platform.security;

import com.bbc.sms.platform.tenant.ParcoursContext;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Server-resolved identifiers used by the policy engine.  It deliberately
 * contains no client-controlled entity objects or DTOs.
 */
public record PolicyResourceContext(
        UUID schoolId,
        UUID academicSessionId,
        LocalDate effectiveDate,
        ParcoursContext.Scope parcours,
        UUID classId,
        String subjectCode,
        UUID studentId,
        UUID timetableOccurrenceId,
        UUID documentId,
        UUID ownerEmployeeId,
        String periodKey,
        String level
) {
    public static PolicyResourceContext empty() {
        return new PolicyResourceContext(null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    public PolicyResourceContext forSchool(UUID id) {
        return new PolicyResourceContext(id, academicSessionId, effectiveDate, parcours,
                classId, subjectCode, studentId, timetableOccurrenceId, documentId,
                ownerEmployeeId, periodKey, level);
    }

    public PolicyResourceContext withOwnerEmployeeId(UUID id) {
        return new PolicyResourceContext(schoolId, academicSessionId, effectiveDate, parcours,
                classId, subjectCode, studentId, timetableOccurrenceId, documentId,
                id, periodKey, level);
    }

    public PolicyResourceContext withParcours(ParcoursContext.Scope value) {
        return new PolicyResourceContext(schoolId, academicSessionId, effectiveDate, value,
                classId, subjectCode, studentId, timetableOccurrenceId, documentId,
                ownerEmployeeId, periodKey, level);
    }
}
