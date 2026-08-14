package com.bbc.sms.finance.plans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "installment_template_line")
@Getter
@Setter
public class InstallmentTemplateLine {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "template_id", nullable = false) private UUID templateId;
    @Column(name = "line_order", nullable = false) private int lineOrder;
    @Column(name = "label_fr", nullable = false, length = 160) private String labelFr;
    @Column(name = "label_en", nullable = false, length = 160) private String labelEn;
    @Column(name = "allocation_type", nullable = false, length = 12) private String allocationType;
    @Column(name = "amount_minor") private Long amountMinor;
    @Column(name = "percentage_basis_points") private Integer percentageBasisPoints;
    @Column(name = "due_rule_type", nullable = false, length = 24) private String dueRuleType;
    @Column(name = "absolute_due_date") private LocalDate absoluteDueDate;
    @Column(name = "due_offset_days") private Integer dueOffsetDays;
    @Column(name = "academic_term_id") private UUID academicTermId;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
