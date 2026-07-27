package com.bbc.sms.finance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "fee_config")
@Getter
@Setter
public class FeeConfig {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String level;

    private String subsystem;

    /**
     * Classe à laquelle cette grille s'applique. {@code null} = grille par défaut du
     * niveau ; renseignée, elle prime sur celle du niveau pour les élèves de la classe.
     */
    @Column(name = "class_id")
    private UUID classId;

    @Column(nullable = false)
    private long total;

    /** Tranches nommées : {@code [{label, amount, dueOn}]} — l'ordre fait foi. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> tranches;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> items;
}
