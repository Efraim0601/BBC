package com.bbc.sms.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {
    List<AttendanceRecord> findBySchoolIdAndDate(UUID schoolId, LocalDate date);
    Optional<AttendanceRecord> findBySchoolIdAndStudentIdAndDate(UUID schoolId, UUID studentId, LocalDate date);
    boolean existsBySchoolIdAndDedupKey(UUID schoolId, String dedupKey);
}
