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
import java.util.UUID;

@Entity
@Table(name = "installment_template")
@Getter
@Setter
public class InstallmentTemplate {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(nullable = false, length = 64) private String code;
    @Column(name = "name_fr", nullable = false, length = 160) private String nameFr;
    @Column(name = "name_en", nullable = false, length = 160) private String nameEn;
    @Column(nullable = false, length = 10) private String lifecycle = "DRAFT";
    @Column(name = "source_session_id") private UUID sourceSessionId;
    @Version private long version;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
