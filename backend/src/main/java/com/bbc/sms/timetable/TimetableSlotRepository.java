package com.bbc.sms.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, UUID> {
    List<TimetableSlot> findBySchoolIdAndClassId(UUID schoolId, UUID classId);
    Optional<TimetableSlot> findBySchoolIdAndClassIdAndDayIdxAndSlotIdx(UUID schoolId, UUID classId, int dayIdx, int slotIdx);
    List<TimetableSlot> findBySchoolIdAndDayIdxAndSlotIdxAndTeacherId(UUID schoolId, int dayIdx, int slotIdx, UUID teacherId);
}
