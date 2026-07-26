package com.bbc.sms.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, UUID> {
    List<TimetableSlot> findBySchoolIdAndClassId(UUID schoolId, UUID classId);
    Optional<TimetableSlot> findBySchoolIdAndClassIdAndDayIdxAndSlotIdx(UUID schoolId, UUID classId, int dayIdx, int slotIdx);
    List<TimetableSlot> findBySchoolIdAndDayIdxAndSlotIdxAndTeacherId(UUID schoolId, int dayIdx, int slotIdx, UUID teacherId);

    @Query("""
        select distinct s.room from TimetableSlot s
        where s.schoolId = :schoolId and s.room is not null and s.room <> ''
        order by s.room
        """)
    List<String> findDistinctRooms(@Param("schoolId") UUID schoolId);
}
