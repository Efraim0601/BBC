package com.bbc.sms.timetable;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "timetable_slot")
@Getter
@Setter
public class TimetableSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "day_idx", nullable = false)
    private int dayIdx;

    @Column(name = "slot_idx", nullable = false)
    private int slotIdx;

    @Column(name = "subject_code")
    private String subjectCode;

    @Column(name = "teacher_id")
    private UUID teacherId;

    private String room;
}
