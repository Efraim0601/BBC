package com.bbc.sms.foundation.enrollment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_enrollment")
@Getter @Setter
public class StudentEnrollment {
    @Id @GeneratedValue private UUID id;
    @Column(name = "school_id", nullable = false) private UUID schoolId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "academic_session_id", nullable = false) private UUID academicSessionId;
    @Column(name = "school_class_id") private UUID schoolClassId;
    @Column(name = "cohort_id") private UUID cohortId;
    @Column(name = "class_name_snapshot") private String classNameSnapshot;
    @Column(name = "level_snapshot") private String levelSnapshot;
    @Column(name = "subsystem_snapshot") private String subsystemSnapshot;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "enrolled_on", nullable = false) private LocalDate enrolledOn;
    @Column(name = "exited_on") private LocalDate exitedOn;
    @Column(nullable = false) private String source = "MANUAL";
    private String reason;
    @Column(name = "previous_enrollment_id") private UUID previousEnrollmentId;
    @Version private long version;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
}
