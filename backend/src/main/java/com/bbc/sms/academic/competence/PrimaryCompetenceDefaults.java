package com.bbc.sms.academic.competence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starting grids for the primary competence-based report card, one per subsystem and level.
 * They follow the school's report books; the school validates or edits them in the app, which
 * publishes a new grid version. A null evaluation type means that type is not assessed.
 */
public final class PrimaryCompetenceDefaults {
    private PrimaryCompetenceDefaults() {}

    /** One sub-competence (taught as a subject) of a grid, with the points of each evaluation type. */
    public record Item(String competenceCode, String code, Map<String, String> label, BigDecimal points,
                       BigDecimal oral, BigDecimal written, BigDecimal practical, BigDecimal attitude,
                       String alternativeGroup, boolean countsInTotal) {}

    /** Competence labels; {@code CX} holds subjects shown on the card but not counted in the total. */
    public static final Map<String, Map<String, String>> COMPETENCES = Map.of(
            "C1", label("Communiquer en français et en anglais et pratiquer au moins une langue nationale",
                    "Communicate in English, French and one national language"),
            "C2", label("Utiliser les notions de base en mathématiques, sciences et technologies",
                    "Use basic notions in mathematics, science and technology"),
            "C3", label("Pratiquer les valeurs sociales et citoyennes", "Practise social and citizenship values"),
            "C4", label("Démontrer l'autonomie, l'esprit d'initiative, la créativité et l'esprit d'entreprise",
                    "Demonstrate autonomy, spirit of initiative, creativity and entrepreneurship"),
            "C5", label("Utiliser les concepts de base et les outils des TIC",
                    "Use basic concepts and tools of information and communication technology"),
            "C6", label("Pratiquer les activités physiques, sportives et artistiques",
                    "Practise physical, sports and artistic activities"),
            "CX", label("Autres activités", "Other activities"));

    public static List<Item> grid(String subsystem, int level) {
        boolean english = "EN".equalsIgnoreCase(subsystem);
        return switch (level) {
            case 1 -> english ? englishLevelOne() : frenchLevelOne();
            case 2 -> english ? englishUpperLevel(false) : frenchUpperLevel();
            case 3 -> english ? englishUpperLevel(true) : frenchUpperLevel();
            default -> throw new IllegalArgumentException("Unknown primary level " + level);
        };
    }

    private static List<Item> frenchLevelOne() {
        List<Item> items = new ArrayList<>();
        items.add(item("C1", "1A", FRENCH, 40, 20, 15, null, 5));
        items.add(item("C1", "1B", ENGLISH, 40, 20, 15, null, 5));
        items.add(item("C1", "1C", NATIONAL, 20, 10, 5, 3, 2));
        items.add(item("C2", "2A", MATHS, 30, 5, 20, null, 5));
        items.add(item("C2", "2B", SCIENCE, 30, 5, 5, 15, 5));
        items.add(item("C3", "3A", SOCIAL, 20, 3, 3, 10, 4));
        items.add(item("C3", "3B", CITIZENSHIP, 20, 5, 5, 8, 2));
        items.add(item("C4", "4", AUTONOMY, 20, 5, 3, 10, 2));
        items.add(item("C5", "5", ICT, 20, 3, 3, 10, 4));
        items.addAll(sport(20, new int[]{3, 3, 10, 4}, new Integer[]{8, 10, null, 2}));
        items.add(item("C6", "6B", ARTS, 20, 4, 3, 10, 3));
        return items;
    }

    private static List<Item> frenchUpperLevel() {
        List<Item> items = new ArrayList<>();
        items.add(item("C1", "1A", FRENCH, 30, 12, 15, null, 3));
        items.add(item("C1", "1B", ENGLISH, 30, 12, 15, null, 3));
        items.add(item("C1", "1C", NATIONAL, 20, 10, 6, 2, 2));
        items.add(item("C2", "2A", MATHS, 40, 8, 28, null, 4));
        items.add(item("C2", "2B", SCIENCE, 40, 6, 7, 20, 7));
        items.add(item("C3", "3A", SOCIAL, 20, 3, 8, 5, 4));
        items.add(item("C3", "3B", CITIZENSHIP, 20, 3, 9, 5, 3));
        items.add(item("C4", "4", AUTONOMY, 20, 5, 2, 11, 2));
        items.add(item("C5", "5", ICT, 40, 4, 10, 20, 6));
        items.addAll(sport(20, new int[]{2, 2, 12, 4}, new Integer[]{3, 15, null, 2}));
        items.add(item("C6", "6B", ARTS, 20, 2, 4, 12, 2));
        return items;
    }

    private static List<Item> englishLevelOne() {
        List<Item> items = new ArrayList<>();
        items.add(item("C1", "1A", ENGLISH, 40, 20, 15, null, 5));
        items.add(item("C1", "1B", FRENCH, 40, 20, 15, null, 5));
        items.add(item("C1", "1C", NATIONAL, 20, 15, null, null, 5));
        items.add(item("C2", "2A", MATHS, 30, 10, 15, 3, 2));
        items.add(item("C2", "2B", SCIENCE, 30, 5, 15, 5, 5));
        items.add(item("C3", "3", SOCIAL_AND_CITIZENSHIP, 30, 10, 5, 5, 10));
        items.add(item("C4", "4A", VOCATIONAL, 15, 2, 4, 6, 3));
        items.add(item("C4", "4B", ENTREPRENEURSHIP, 15, 2, 4, 6, 3));
        items.add(item("C5", "5", ICT, 20, 3, 4, 10, 3));
        items.addAll(sport(20, new int[]{3, 4, 10, 3}, new Integer[]{12, null, null, 8}));
        items.add(item("C6", "6B", ARTS, 20, 3, 4, 10, 3));
        return items;
    }

    private static List<Item> englishUpperLevel(boolean levelThree) {
        List<Item> items = new ArrayList<>();
        items.add(item("C1", "1A", ENGLISH, 40, 20, 15, null, 5));
        items.add(item("C1", "1B", FRENCH, 40, 20, 15, null, 5));
        items.add(item("C1", "1C", NATIONAL, 20, 15, null, null, 5));
        items.add(item("C2", "2A", MATHS, 50, 10, 20, 15, 5));
        items.add(item("C2", "2B", SCIENCE, 50, 10, 20, 15, 5));
        items.add(item("C3", "3A", SOCIAL, 20, 6, 2, 2, 10));
        items.add(item("C3", "3B", CITIZENSHIP, 20, 6, 2, 2, 10));
        items.add(item("C4", "4A", VOCATIONAL, 20, 3, 5, 10, 2));
        items.add(item("C4", "4B", ENTREPRENEURSHIP, 20, 3, 5, 10, 2));
        items.add(item("C5", "5", ICT, 40, 5, 10, 20, 5));
        items.addAll(sport(20, new int[]{3, 4, 10, 3}, new Integer[]{12, null, null, 8}));
        items.add(levelThree ? item("C6", "6B", ARTS, 20, 4, 2, 10, 4) : item("C6", "6B", ARTS, 20, 2, 8, 8, 2));
        return items;
    }

    private static List<Item> sport(int points, int[] able, Integer[] challenged) {
        return List.of(
                new Item("C6", "6A1", SPORT_ABLE, BigDecimal.valueOf(points), value(able[0]), value(able[1]),
                        value(able[2]), value(able[3]), "6A", true),
                new Item("C6", "6A2", SPORT_CHALLENGED, BigDecimal.valueOf(points), value(challenged[0]),
                        value(challenged[1]), value(challenged[2]), value(challenged[3]), "6A", true));
    }

    private static Item item(String competence, String code, Map<String, String> label, int points,
                             Integer oral, Integer written, Integer practical, Integer attitude) {
        return new Item(competence, code, label, BigDecimal.valueOf(points), value(oral), value(written),
                value(practical), value(attitude), null, true);
    }

    private static BigDecimal value(Integer points) { return points == null ? null : BigDecimal.valueOf(points); }

    private static Map<String, String> label(String fr, String en) { return Map.of("fr", fr, "en", en); }

    private static final Map<String, String> FRENCH = label("Communiquer en français", "Communicate in French");
    private static final Map<String, String> ENGLISH = label("Communiquer en anglais", "Communicate in English");
    private static final Map<String, String> NATIONAL = label("Pratiquer une langue nationale", "Communicate in one national language");
    private static final Map<String, String> MATHS = label("Utiliser les notions de base en mathématiques", "Use basic notions in mathematics");
    private static final Map<String, String> SCIENCE = label("Utiliser les notions de base en sciences et technologies",
            "Use basic notions in science and technology");
    private static final Map<String, String> SOCIAL = label("Pratiquer les valeurs sociales", "Practise social values");
    private static final Map<String, String> CITIZENSHIP = label("Pratiquer les valeurs citoyennes", "Practise citizenship values");
    private static final Map<String, String> SOCIAL_AND_CITIZENSHIP = label("Pratiquer les valeurs sociales et citoyennes",
            "Practise social and citizenship values");
    private static final Map<String, String> AUTONOMY = label("Démontrer l'autonomie, l'esprit d'initiative, la créativité et l'esprit d'entreprise",
            "Demonstrate autonomy, initiative, creativity and entrepreneurship");
    private static final Map<String, String> VOCATIONAL = label("Autonomie et créativité dans les études professionnelles",
            "Autonomy, initiative and creativity in vocational studies");
    private static final Map<String, String> ENTREPRENEURSHIP = label("Esprit d'initiative et d'entreprise",
            "Spirit of initiative and entrepreneurship");
    private static final Map<String, String> ICT = label("Utiliser les concepts de base et les outils des TIC",
            "Use basic concepts and tools of ICT");
    private static final Map<String, String> SPORT_ABLE = label("Activités physiques et sportives (apprenants aptes)",
            "Physical and sports activities for normal pupils");
    private static final Map<String, String> SPORT_CHALLENGED = label("Activités physiques et sportives (apprenants inaptes)",
            "Physical and sports activities for physically challenged");
    private static final Map<String, String> ARTS = label("Pratiquer les activités artistiques", "Practise artistic activities");
}
