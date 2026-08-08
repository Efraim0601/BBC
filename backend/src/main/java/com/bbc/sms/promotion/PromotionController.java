package com.bbc.sms.promotion;

import com.bbc.sms.promotion.dto.PromotionDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Passage de classe.
 *
 * <p>Deux niveaux de droits, volontairement distincts : <b>configurer</b> la
 * progression et les seuils relève des Paramètres (administrateur) ; <b>exécuter</b>
 * le passage relève du module Passage de classe (direction, censeur). Un censeur
 * applique donc les règles sans pouvoir les réécrire.
 */
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private static final String READ =
        "@parcours.allows() and (@perm.can('promotion','read') or @perm.can('settings','read'))";
    private static final String RUN = "@parcours.allows() and @perm.can('promotion','write')";
    private static final String CONFIG = "@parcours.allows() and @perm.can('settings','write')";
    /**
     * La clôture archive et bascule toute l'école : elle exige les deux droits,
     * et un compte qui répond de l'école entière. Un admin de section ne peut
     * pas clore une année pour les deux autres cycles.
     */
    private static final String CLOSE = "@perm.can('settings','write') and @perm.can('promotion','write')"
        + " and @perm.schoolWide()";

    private final ProgressionService progression;
    private final PromotionService promotions;
    private final YearClosureService closures;

    public PromotionController(ProgressionService progression, PromotionService promotions,
                               YearClosureService closures) {
        this.progression = progression;
        this.promotions = promotions;
        this.closures = closures;
    }

    // ---- Configuration -------------------------------------------------------

    @GetMapping("/config")
    @PreAuthorize(READ)
    public PromotionConfig config() { return progression.config(); }

    @PutMapping("/progression")
    @PreAuthorize(CONFIG)
    public List<ProgressionView> saveProgression(@Valid @RequestBody ProgressionUpdate in) {
        return progression.saveProgression(in);
    }

    /** Déduit ordre et enchaînements des libellés officiels — proposition à relire. */
    @PostMapping("/progression/auto")
    @PreAuthorize(CONFIG)
    public List<ProgressionView> autoProgression() { return progression.autoProgression(); }

    @PostMapping("/rules")
    @PreAuthorize(CONFIG)
    public List<RuleView> saveRule(@Valid @RequestBody RuleUpsert in) { return progression.saveRule(in); }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize(CONFIG)
    public List<RuleView> deleteRule(@PathVariable UUID id) { return progression.deleteRule(id); }

    // ---- Exécution -----------------------------------------------------------

    @GetMapping("/preview")
    @PreAuthorize(RUN)
    public PromotionPreview preview(@RequestParam UUID classId,
                                    @RequestParam(required = false) String academicYear) {
        return promotions.preview(classId, academicYear);
    }

    @PostMapping("/apply")
    @PreAuthorize(RUN)
    public PromotionResult apply(@Valid @RequestBody PromotionApply in) { return promotions.apply(in); }

    @GetMapping("/batches")
    @PreAuthorize(READ)
    public List<BatchView> batches() { return promotions.history(); }

    // ---- Clôture de l'année ---------------------------------------------------

    @GetMapping("/closure/preview")
    @PreAuthorize(CLOSE)
    public ClosurePreview closurePreview(@RequestParam(required = false) String academicYear) {
        return closures.preview(academicYear);
    }

    @PostMapping("/closure")
    @PreAuthorize(CLOSE)
    public ClosureResult close(@Valid @RequestBody ClosureRequest in) { return closures.close(in); }

    @GetMapping("/closure/history")
    @PreAuthorize(CLOSE)
    public List<ClosureView> closureHistory() { return closures.history(); }
}
