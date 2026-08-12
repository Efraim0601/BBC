package com.bbc.sms.platform.security;

import java.util.Map;

/** Stable action codes used by method security and the Angular action catalogue. */
public final class PermissionActions {
    private PermissionActions() {}

    public record Requirement(String module, String level) {}

    public static final Map<String, Requirement> CATALOG = Map.ofEntries(
            Map.entry("SESSION_VIEW", new Requirement("settings", "read")),
            Map.entry("SESSION_MANAGE", new Requirement("settings", "write")),
            Map.entry("ACADEMIC_WINDOW_OVERRIDE", new Requirement("settings", "write")),
            Map.entry("ENROLLMENT_VIEW", new Requirement("students", "read")),
            Map.entry("ENROLLMENT_MANAGE", new Requirement("students", "write")),
            Map.entry("CALENDAR_VIEW", new Requirement("settings", "read")),
            Map.entry("CALENDAR_MANAGE", new Requirement("settings", "write")),
            Map.entry("AUDIT_VIEW", new Requirement("settings", "read")),
            Map.entry("DOCUMENT_VIEW", new Requirement("documents", "read")),
            Map.entry("DOCUMENT_DESIGN_PUBLISH", new Requirement("settings", "write")),
            Map.entry("DOCUMENT_GENERATE", new Requirement("documents", "write")),
            Map.entry("DOCUMENT_REVOKE", new Requirement("documents", "write")),
            Map.entry("GUARDIAN_LINK", new Requirement("students", "write")),
            Map.entry("ATTENDANCE_MARK", new Requirement("presence", "write")),
            Map.entry("ATTENDANCE_FINALIZE", new Requirement("presence", "write")),
            Map.entry("ATTENDANCE_REOPEN", new Requirement("presence", "write")),
            Map.entry("ATTENDANCE_ADJUSTMENT_VIEW", new Requirement("presence", "read")),
            Map.entry("ATTENDANCE_ADJUSTMENT_EDIT", new Requirement("presence", "write")),
            Map.entry("ATTENDANCE_ADJUSTMENT_REVIEW", new Requirement("academic", "write")),
            Map.entry("COUNCIL_INPUT_VIEW", new Requirement("academic", "read")),
            Map.entry("COUNCIL_INPUT_EDIT", new Requirement("academic", "write")),
            Map.entry("COUNCIL_INPUT_REVIEW", new Requirement("academic", "write")),
            Map.entry("COUNCIL_OVERRIDE", new Requirement("academic", "write")),
            Map.entry("GRADE_SUBMIT", new Requirement("academic", "write")),
            Map.entry("GRADE_REVIEW", new Requirement("academic", "write")),
            Map.entry("BULLETIN_VALIDATE", new Requirement("academic", "write")),
            Map.entry("BULLETIN_PUBLISH", new Requirement("academic", "write")),
            Map.entry("PROMOTION_RECOMMEND", new Requirement("academic", "write")),
            Map.entry("PROMOTION_OVERRIDE", new Requirement("academic", "write")),
            Map.entry("PROMOTION_COMMIT", new Requirement("academic", "write")),
            Map.entry("PROGRESSION_VIEW", new Requirement("journey", "read")),
            Map.entry("PROGRESSION_CONFIGURE", new Requirement("journey", "write")),
            Map.entry("PROMOTION_REVIEW", new Requirement("journey", "write")),
            Map.entry("PROMOTION_CONFIGURE", new Requirement("journey", "write")),
            Map.entry("PROMOTION_CORRECT", new Requirement("journey", "write")),
            Map.entry("FEE_CONFIGURE", new Requirement("finance", "write")),
            Map.entry("PAYMENT_COLLECT", new Requirement("finance", "write")),
            Map.entry("PAYMENT_REVERSE", new Requirement("finance", "write")),
            Map.entry("LEDGER_POST", new Requirement("finance", "write")),
            Map.entry("LEDGER_CLOSE", new Requirement("finance", "write")),
            Map.entry("PAYROLL_APPROVE", new Requirement("hr", "write")),
            Map.entry("PAYROLL_PAY", new Requirement("hr", "write")),
            Map.entry("TIMETABLE_DRAFT", new Requirement("timetable", "write")),
            Map.entry("TIMETABLE_PUBLISH", new Requirement("timetable", "write")),
            Map.entry("TIMETABLE_OVERRIDE", new Requirement("timetable", "write")),
            Map.entry("HEALTH_CONFIDENTIAL_VIEW", new Requirement("health", "write"))
    );
}
