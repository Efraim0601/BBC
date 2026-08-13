package com.bbc.sms.foundation.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_term")
@Getter @Setter
public class AcademicTerm {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(nullable = false) private String code;
    @Column(nullable = false) private String label;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Column(name = "grade_entry_opens_at") private Instant gradeEntryOpensAt;
    @Column(name = "grade_entry_closes_at") private Instant gradeEntryClosesAt;
    @Column(name = "bulletin_publish_opens_at") private Instant bulletinPublishOpensAt;
    @Column(name = "bulletin_publish_closes_at") private Instant bulletinPublishClosesAt;
    @Column(name = "teacher_submission_opens_at") private Instant teacherSubmissionOpensAt;
    @Column(name = "teacher_submission_closes_at") private Instant teacherSubmissionClosesAt;
    /** V85 authoritative optional trimester-wide date gate. */
    @Column(name = "management_window_limited", nullable = false) private boolean managementWindowLimited;
    @Column(name = "management_opens_at") private Instant managementOpensAt;
    @Column(name = "management_closes_at") private Instant managementClosesAt;
    @Column(nullable = false) private String timezone = "Africa/Douala";
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
