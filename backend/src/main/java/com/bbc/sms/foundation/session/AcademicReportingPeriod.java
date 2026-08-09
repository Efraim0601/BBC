package com.bbc.sms.foundation.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_reporting_period")
@Getter
@Setter
public class AcademicReportingPeriod {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "academic_term_id") private UUID academicTermId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String label;
    @Column(name = "period_type", nullable = false) private String periodType;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "grade_entry_opens_at") private Instant gradeEntryOpensAt;
    @Column(name = "grade_entry_closes_at") private Instant gradeEntryClosesAt;
    @Column(name = "review_opens_at") private Instant reviewOpensAt;
    @Column(name = "review_closes_at") private Instant reviewClosesAt;
    @Column(name = "validation_opens_at") private Instant validationOpensAt;
    @Column(name = "validation_closes_at") private Instant validationClosesAt;
    @Column(name = "bulletin_publish_opens_at") private Instant bulletinPublishOpensAt;
    @Column(name = "bulletin_publish_closes_at") private Instant bulletinPublishClosesAt;
    @Column(name = "correction_opens_at") private Instant correctionOpensAt;
    @Column(name = "correction_closes_at") private Instant correctionClosesAt;
    @Column(name = "calculation_policy", nullable = false) private String calculationPolicy = "DEFAULT";
    @Column(nullable = false) private String status = "DRAFT";
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
