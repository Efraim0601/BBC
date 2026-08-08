package com.bbc.sms.promotion;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Échelle des classes camerounaises, par cycle et sous-système.
 *
 * <p>Sert uniquement à la déduction automatique du mapping de progression : elle
 * propose un ordre (SIL → CP → CE1 …) que l'administrateur relit et corrige. Rien
 * dans le calcul de passage n'en dépend — une école aux libellés exotiques
 * configure sa progression à la main et fonctionne identiquement.
 */
final class ClassLadder {

    /** Un barreau de l'échelle : ses écritures possibles, la plus longue gagnant au match. */
    private record Rung(List<String> patterns) {}

    private record Ladder(String level, String subsystem, List<Rung> rungs) {}

    private static Rung r(String... patterns) { return new Rung(List.of(patterns)); }

    private static final List<Ladder> LADDERS = List.of(
        new Ladder("maternelle", "FR", List.of(
            r("petite section", "ps"), r("moyenne section", "ms"), r("grande section", "gs"))),
        new Ladder("maternelle", "EN", List.of(
            r("pre nursery", "prenursery"), r("nursery 1", "nursery1"),
            r("nursery 2", "nursery2"), r("nursery 3", "nursery3"))),
        new Ladder("primary", "FR", List.of(
            r("sil"), r("cp"), r("ce1"), r("ce2"), r("cm1"), r("cm2"))),
        new Ladder("primary", "EN", List.of(
            r("class 1", "class1"), r("class 2", "class2"), r("class 3", "class3"),
            r("class 4", "class4"), r("class 5", "class5"), r("class 6", "class6"))),
        new Ladder("secondary", "FR", List.of(
            r("6e", "6eme", "sixieme"), r("5e", "5eme", "cinquieme"),
            r("4e", "4eme", "quatrieme"), r("3e", "3eme", "troisieme"),
            r("2nde", "2nd", "2de", "seconde"), r("1ere", "1re", "1er", "premiere"),
            r("terminale", "tle", "term"))),
        new Ladder("secondary", "EN", List.of(
            r("form 1", "form1"), r("form 2", "form2"), r("form 3", "form3"),
            r("form 4", "form4"), r("form 5", "form5"),
            r("lower sixth", "lower 6", "form 6", "form6"),
            r("upper sixth", "upper 6", "form 7", "form7")))
    );

    private ClassLadder() {}

    /** Position reconnue d'une classe sur son échelle : rang 1..n, suffixe, fin de cycle. */
    record Match(int order, String suffix, boolean lastRung) {}

    /**
     * Reconnaît le barreau d'une classe par le plus long préfixe correspondant —
     * « 3e » et « 3eme A » tombent sur le même rang, « CE1 » ne prend pas « CE2 ».
     * Renvoie null quand le libellé n'appartient à aucune échelle connue.
     */
    static Match match(String level, String subsystem, String className) {
        String name = normalize(className);
        if (name.isEmpty()) return null;

        for (Ladder ladder : LADDERS) {
            if (!ladder.level().equals(level) || !ladder.subsystem().equals(subsystem)) continue;

            int bestOrder = -1;
            String bestPattern = null;
            for (int i = 0; i < ladder.rungs().size(); i++) {
                for (String pattern : ladder.rungs().get(i).patterns()) {
                    if (!name.startsWith(pattern)) continue;
                    if (bestPattern == null || pattern.length() > bestPattern.length()) {
                        bestPattern = pattern;
                        bestOrder = i;
                    }
                }
            }
            if (bestPattern == null) return null;
            String suffix = name.substring(bestPattern.length()).trim();
            return new Match(bestOrder + 1, suffix, bestOrder == ladder.rungs().size() - 1);
        }
        return null;
    }

    /** Minuscules, sans accents, ponctuation en espaces, espaces compactés. */
    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
