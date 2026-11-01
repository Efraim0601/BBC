package com.bbc.sms.timetable;

/**
 * The school uses different bell schedules in its primary/kindergarten and
 * secondary cycles.  Slots remain numbered from zero, but a number is only
 * meaningful inside the period profile of the class it belongs to.
 */
public final class TimetablePeriodProfile {
    public static final String PRIMARY = "PRIMARY";
    public static final String SECONDARY = "SECONDARY";

    private TimetablePeriodProfile() {}

    public static String forLevel(String level) {
        return "secondary".equalsIgnoreCase(level) ? SECONDARY : PRIMARY;
    }

    public static String normalize(String profile) {
        if (profile == null || profile.isBlank()) return PRIMARY;
        String normalized = profile.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case PRIMARY, "MATERNELLE", "KINDERGARTEN" -> PRIMARY;
            case SECONDARY -> SECONDARY;
            default -> throw new IllegalArgumentException("Profil horaire inconnu");
        };
    }
}
