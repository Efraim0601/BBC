package com.bbc.sms.promotion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Règle de passage : le seuil à partir duquel la moyenne annuelle propose
 * l'admission. Le périmètre va du plus large au plus fin — école entière
 * (tout à null), parcours (niveau + sous-système), ou classe précise — et la
 * règle la plus spécifique l'emporte au moment du calcul.
 */
@Entity
@Table(name = "year_promotion_rule")
@Getter
@Setter
public class PromotionRule {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    /** maternelle | primary | secondary — null = tous les niveaux. */
    private String level;

    /** FR | EN — null = les deux sous-systèmes. */
    private String subsystem;

    /** Classe visée — null = toutes les classes du périmètre (level, subsystem). */
    @Column(name = "class_id")
    private UUID classId;

    /** Moyenne annuelle minimale pour être proposé admis (défaut 10/20). */
    @Column(name = "pass_mark", nullable = false)
    private BigDecimal passMark = new BigDecimal("10.00");

    /** Zone grise sous le seuil : proposé « à examiner » plutôt que redoublant. */
    @Column(name = "council_margin", nullable = false)
    private BigDecimal councilMargin = BigDecimal.ZERO;

    /** Nombre de redoublements déjà subis au-delà duquel on renvoie au conseil. */
    @Column(name = "max_repeats")
    private Integer maxRepeats;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Spécificité du périmètre : plus le chiffre est haut, plus la règle prime.
     * Classe (4) &gt; niveau + sous-système (3) &gt; niveau ou sous-système (1) &gt; école (0).
     */
    public int specificity() {
        if (classId != null) return 4;
        int s = 0;
        if (level != null) s += 2;
        if (subsystem != null) s += 1;
        return s == 3 ? 3 : s;
    }
}
