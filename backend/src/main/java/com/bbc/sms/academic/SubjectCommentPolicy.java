package com.bbc.sms.academic;

import com.bbc.sms.platform.common.ApiException;

import java.util.Locale;
import java.util.Set;

/** Canonical server-side policy for teacher remarks. */
public final class SubjectCommentPolicy {
    public static final int MAX_LENGTH = 500;
    public static final Set<String> APPRECIATION_CODES = Set.of(
            "ENCOURAGEMENT", "CONGRATULATIONS", "HONOR_ROLL", "WORK_WARNING", "CONDUCT_WARNING");

    private SubjectCommentPolicy() {}

    public static String sanitize(String raw) {
        if (raw == null) return null;
        String text = raw.replaceAll("<[^>]*>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "")
                .trim();
        if (text.length() > MAX_LENGTH) {
            throw ApiException.badRequest("La remarque ne peut pas dépasser " + MAX_LENGTH + " caractères.");
        }
        return text.isBlank() ? null : text;
    }

    public static String appreciation(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!APPRECIATION_CODES.contains(code)) {
            throw ApiException.badRequest("Le code d'appréciation n'est pas autorisé.");
        }
        return code;
    }
}
