package com.bbc.sms.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "academic_grade_packet")
@Getter
@Setter
public class AcademicGradePacket {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "reporting_period_id", nullable = false) private UUID reportingPeriodId;
    @Column(name = "class_id", nullable = false) private UUID classId;
    @Column(name = "subject_code", nullable = false) private String subjectCode;
    @Column(name = "teacher_id") private UUID teacherId;
    @Column(name = "responsible_assignment_id") private UUID responsibleAssignmentId;
    @Column(name = "responsible_assignment_version") private Long responsibleAssignmentVersion;
    @Column(name = "last_saved_by") private UUID lastSavedBy;
    @Column(name = "last_saved_at") private Instant lastSavedAt;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(name = "submitted_by") private UUID submittedBy;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_reason") private String reviewReason;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}

