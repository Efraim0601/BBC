package com.bbc.sms.platform.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Les rôles d'administrateur de section et la section qu'ils commandent.
 *
 * <p>Un admin de section administre son cycle comme l'admin principal administre
 * l'école : mêmes écrans, mêmes droits d'écriture, mais rien au-delà de sa
 * section. Le cloisonnement ne tient pas à la matrice des permissions — celle-ci
 * raisonne par module, jamais par section — mais à ce verrou, appliqué à chaque
 * requête.
 *
 * <p>La section se déduit du seul code de rôle, lequel voyage déjà dans le JWT :
 * aucune lecture en base n'est nécessaire pour savoir qui est cloisonné, et le
 * filtre d'authentification reste sans requête SQL. C'est la raison d'être des
 * trois rôles distincts, là où un rôle unique aurait exigé une colonne de plus
 * sur {@code app_user} — et donc un aller-retour en base par requête.
 */
public final class SectionRoles {

    /** Rôle → section administrée, dans l'ordre de la scolarité. */
    private static final Map<String, String> SECTION_BY_ROLE;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("admin_maternelle", "maternelle");
        m.put("admin_primary", "primary");
        m.put("admin_secondary", "secondary");
        SECTION_BY_ROLE = Collections.unmodifiableMap(m);
    }

    /** Sections reconnues, dans l'ordre de la scolarité. */
    public static final Set<String> SECTIONS = Set.of("maternelle", "primary", "secondary");

    private SectionRoles() {}

    /** Section administrée par ce rôle, ou null si le rôle n'est pas cloisonné. */
    public static String sectionOf(String roleCode) {
        return roleCode == null ? null : SECTION_BY_ROLE.get(roleCode);
    }

    /** Le rôle qui administre cette section, ou null si la section est inconnue. */
    public static String roleFor(String section) {
        if (section == null) return null;
        for (Map.Entry<String, String> e : SECTION_BY_ROLE.entrySet()) {
            if (e.getValue().equals(section)) return e.getKey();
        }
        return null;
    }

    /** Ce rôle est-il celui d'un administrateur de section ? */
    public static boolean isSectionAdmin(String roleCode) {
        return sectionOf(roleCode) != null;
    }

    /**
     * Les rôles qu'un compte ne peut ni s'attribuer ni attribuer à autrui sans
     * être l'admin principal : les trois admins de section, et le principal
     * lui-même. Sans cela, un admin de section se hisserait au rang supérieur
     * en modifiant sa propre fiche employé.
     */
    public static Set<String> privilegedRoles() {
        Set<String> roles = new HashSet<>(SECTION_BY_ROLE.keySet());
        roles.add("principal");
        return Set.copyOf(roles);
    }

    /** Codes des trois rôles d'admin de section, dans l'ordre de la scolarité. */
    public static Set<String> sectionAdminRoles() {
        return SECTION_BY_ROLE.keySet();
    }
}
