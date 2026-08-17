package com.bbc.sms.academic.security;

import com.bbc.sms.platform.security.PolicyResourceContext;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Adapter between the central policy vocabulary and the existing, authoritative
 * AcademicAccessPolicyService.  Assignment/date/session logic remains in that
 * service; this class only translates stable action codes into capabilities.
 */
@Service
public class AcademicScopeResolver {
    private final AcademicAccessPolicyService accessPolicy;

    public AcademicScopeResolver(AcademicAccessPolicyService accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    public boolean can(String actionCode, PolicyResourceContext context) {
        AcademicAccessPolicyService.Capability capability = capability(actionCode);
        if (capability == null || context == null || context.academicSessionId() == null
                || context.classId() == null || context.effectiveDate() == null) {
            return false;
        }
        return accessPolicy.canForCentral(capability, context.academicSessionId(), context.classId(),
                context.subjectCode(), context.studentId(), context.effectiveDate());
    }

    public AcademicAccessPolicyService.AccessDecision require(String actionCode,
                                                               PolicyResourceContext context) {
        AcademicAccessPolicyService.Capability capability = capability(actionCode);
        if (capability == null) {
            throw com.bbc.sms.platform.common.ApiException.coded(
                    org.springframework.http.HttpStatus.FORBIDDEN, "ACTION_UNKNOWN",
                    "Cette capacité académique n'est pas configurée.");
        }
        return accessPolicy.requireDomain(capability, context.academicSessionId(), context.classId(),
                context.subjectCode(), context.studentId(), context.effectiveDate());
    }

    private AcademicAccessPolicyService.Capability capability(String raw) {
        String action = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "STUDENT_DIRECTORY_VIEW", "STUDENT_PROFILE_VIEW", "STUDENT_PHOTO_VIEW",
                    "GUARDIAN_VIEW", "ENROLLMENT_VIEW", "ACADEMIC_ROSTER_VIEW" ->
                    AcademicAccessPolicyService.Capability.ACADEMIC_ROSTER_VIEW;
            case "ACADEMIC_ASSESSMENT_VIEW" -> AcademicAccessPolicyService.Capability.ASSESSMENT_VIEW;
            case "ACADEMIC_ASSESSMENT_MANAGE" -> AcademicAccessPolicyService.Capability.ASSESSMENT_MANAGE;
            case "ACADEMIC_SUBJECT_GRADE_VIEW" -> AcademicAccessPolicyService.Capability.SUBJECT_GRADE_VIEW;
            case "ACADEMIC_SUBJECT_GRADE_EDIT" -> AcademicAccessPolicyService.Capability.SUBJECT_GRADE_EDIT;
            case "GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS" ->
                    AcademicAccessPolicyService.Capability.TITULAIRE_ANY_SUBJECT_GRADE_EDIT;
            case "GRADE_SUBMIT" -> AcademicAccessPolicyService.Capability.SUBJECT_GRADE_SUBMIT;
            case "ACADEMIC_CLASS_RESULTS_VIEW" -> AcademicAccessPolicyService.Capability.CLASS_RESULTS_VIEW;
            case "ACADEMIC_REPORT_CARD_VIEW" -> AcademicAccessPolicyService.Capability.CLASS_REPORT_CARD_VIEW;
            case "ACADEMIC_GRADE_PACKET_REVIEW" -> AcademicAccessPolicyService.Capability.GRADE_PACKET_REVIEW;
            case "ACADEMIC_REPORT_CARD_VALIDATE", "BULLETIN_VALIDATE" ->
                    AcademicAccessPolicyService.Capability.REPORT_CARD_VALIDATE;
            case "ACADEMIC_REPORT_CARD_PUBLISH", "BULLETIN_PUBLISH" ->
                    AcademicAccessPolicyService.Capability.REPORT_CARD_PUBLISH;
            case "ACADEMIC_COUNCIL_INPUT_VIEW" -> AcademicAccessPolicyService.Capability.COUNCIL_INPUT_VIEW;
            case "ACADEMIC_COUNCIL_INPUT_EDIT" -> AcademicAccessPolicyService.Capability.COUNCIL_INPUT_EDIT;
            default -> null;
        };
    }
}
