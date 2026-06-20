package com.bbc.sms.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_record")
@Getter
@Setter
public class AttendanceRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "att_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String status;            // present | late | absent

    @Column(name = "check_in_time")
    private String checkInTime;       // HH:mm

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes = 0;

    @Column(nullable = false)
    private String source = "manual"; // fingerprint | manual

    @Column(name = "dedup_key")
    private String dedupKey;
}
