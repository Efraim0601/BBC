package com.bbc.sms.academic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subject_result_comment")
@Getter @Setter
public class SubjectResultComment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "reporting_period_id", nullable = false) private UUID reportingPeriodId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "enrollment_id") private UUID enrollmentId;
    @Column(name = "subject_code", nullable = false) private String subjectCode;
    @Column(name = "teacher_id") private UUID teacherId;
    @Column(columnDefinition = "text") private String comment;
    @Column(name = "appreciation_code") private String appreciationCode;
    @Column(name = "workflow_status", nullable = false) private String workflowStatus = "DRAFT";
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
