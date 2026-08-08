package com.bbc.sms.platform.tenant;

/**
 * Holds the current request's parcours scope (level + subsystem), derived from the
 * optional {@code X-Parcours} header. When present, list services narrow their results
 * to this parcours so the UI is compartmentalised (Maternelle / Primaire / Secondaire,
 * Francophone / Anglophone). Absent scope = no narrowing (e.g. cross-parcours admin views).
 *
 * <p>À côté de ce filtre <em>choisi</em>, la requête porte un filtre <em>subi</em> :
 * le verrou de section ({@link #sectionLock()}). Un administrateur de section ne
 * voit jamais que son cycle, qu'il ait choisi un parcours ou non — c'est
 * précisément le cas où l'absence d'en-tête ne doit surtout pas valoir « tout
 * l'établissement ». Les deux se cumulent : le parcours affine, le verrou borne.
 */
public final class ParcoursContext {

    /** A parcours scope: level in {maternelle,primary,secondary}, subsystem in {FR,EN}. */
    public record Scope(String level, String subsystem) {}

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> SECTION_LOCK = new ThreadLocal<>();

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

    /**
     * Section imposée au compte courant (admin de section), ou null s'il n'est
     * pas cloisonné. Posée par le filtre d'authentification à partir du seul
     * code de rôle : aucune lecture en base.
     */
    public static void lockSection(String level) { SECTION_LOCK.set(level); }

    /**
     * Le cycle auquel la requête est bornée, quel que soit l'en-tête envoyé —
     * null quand le compte n'est pas cloisonné.
     *
     * <p>Les services qui filtrent déjà par parcours doivent AUSSI honorer ce
     * verrou : le parcours vient du client et peut être omis, le verrou non.
     */
    public static String sectionLock() { return SECTION_LOCK.get(); }

    /**
     * Le niveau à appliquer aux listes : le verrou de section s'il existe, sinon
     * le niveau du parcours choisi. Null quand rien ne borne la requête.
     */
    public static String effectiveLevel() {
        String locked = SECTION_LOCK.get();
        if (locked != null) return locked;
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.level();
    }

    public static void clear() {
        CURRENT.remove();
        SECTION_LOCK.remove();
    }
}
