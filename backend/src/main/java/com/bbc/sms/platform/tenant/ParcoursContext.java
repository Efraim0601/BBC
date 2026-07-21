package com.bbc.sms.platform.tenant;

/**
 * Holds the current request's parcours scope (level + subsystem), derived from the
 * optional {@code X-Parcours} header. When present, list services narrow their results
 * to this parcours so the UI is compartmentalised (Maternelle / Primaire / Secondaire,
 * Francophone / Anglophone). Absent scope = no narrowing (e.g. cross-parcours admin views).
 */
public final class ParcoursContext {

    /** A parcours scope: level in {maternelle,primary,secondary}, subsystem in {FR,EN}. */
    public record Scope(String level, String subsystem) {}

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private ParcoursContext() {}

    /** Parse a {@code level:subsystem} header value; returns null when blank/malformed. */
    public static Scope parse(String header) {
        if (header == null || header.isBlank()) return null;
        String[] parts = header.trim().split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
        return new Scope(parts[0].trim().toLowerCase(), parts[1].trim().toUpperCase());
    }

    public static void set(Scope scope) { CURRENT.set(scope); }

    /** The active scope, or null when none is bound to the request. */
    public static Scope get() { return CURRENT.get(); }

    public static boolean isSet() { return CURRENT.get() != null; }

    public static void clear() { CURRENT.remove(); }
}
