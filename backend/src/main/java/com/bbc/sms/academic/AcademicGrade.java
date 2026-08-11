package com.bbc.sms.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "academic_grade")
@Getter @Setter
public class AcademicGrade {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "reporting_period_id", nullable = false) private UUID reportingPeriodId;
    @Column(name = "assessment_id", nullable = false) private UUID assessmentId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "enrollment_id") private UUID enrollmentId;
    @Column(name = "subject_code", nullable = false) private String subjectCode;
    @Column(name = "entered_by") private UUID enteredBy;
    @Column(name = "teacher_id") private UUID teacherId;
    private BigDecimal mark;
    @Column(name = "value_status", nullable = false) private String valueStatus = "MISSING";
    @Column(name = "workflow_status", nullable = false) private String workflowStatus = "DRAFT";
    @Column(name = "legacy_secondary_mark_id") private UUID legacySecondaryMarkId;
    @Column(name = "curriculum_version_id") private UUID curriculumVersionId;
    @Column(name = "curriculum_subject_id") private UUID curriculumSubjectId;
    @Column(name = "responsible_assignment_id") private UUID responsibleAssignmentId;
    @Column(name = "last_request_id") private UUID lastRequestId;
    @Column(name = "packet_id") private UUID packetId;
    @Column(name = "packet_revision") private int packetRevision = 1;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
