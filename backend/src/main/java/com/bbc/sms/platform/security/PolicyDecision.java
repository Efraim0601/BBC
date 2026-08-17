package com.bbc.sms.platform.security;

/** Structured, localized result shared by HTTP enforcement and the UI. */
public record PolicyDecision(
        boolean allowed,
        String actionCode,
        String denialCode,
        String messageFr,
        String messageEn,
        String winningRuleSource,
        String matchedScope,
        long policyVersion,
        String repairHint
) {
    public static PolicyDecision allow(String actionCode, String source, String scope,
                                       long version) {
        return new PolicyDecision(true, actionCode, null,
                "Accès autorisé.", "Access allowed.", source, scope, version, null);
    }

    public static PolicyDecision deny(String actionCode, String code, String fr, String en,
                                      long version, String repairHint) {
        return new PolicyDecision(false, actionCode, code, fr, en,
                null, null, version, repairHint);
    }
}
