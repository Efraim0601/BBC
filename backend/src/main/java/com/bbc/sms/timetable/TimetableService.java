package com.bbc.sms.timetable;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.timetable.dto.TimetableDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TimetableService {

    private final SchoolClassRepository classRepo;
    private final TimetableSlotRepository slotRepo;

    public TimetableService(SchoolClassRepository classRepo, TimetableSlotRepository slotRepo) {
        this.classRepo = classRepo;
        this.slotRepo = slotRepo;
    }

    @Transactional(readOnly = true)
    public List<ClassRef> classes() {
        UUID schoolId = TenantContext.get();
        return classRepo.findBySchoolIdOrderByName(schoolId).stream()
                .map(this::toRef)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SlotView> grid(String className) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = findClass(schoolId, className);
        return slotRepo.findBySchoolIdAndClassId(schoolId, cls.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> rooms() {
        return slotRepo.findDistinctRooms(TenantContext.get());
    }

    @Transactional
    public SlotSaveResult upsertSlot(SlotUpsert in) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = findClass(schoolId, in.className());

        // Conflict detection: a teacher booked elsewhere at the same day/slot.
        List<Conflict> conflicts = new ArrayList<>();
        if (in.teacherId() != null) {
            List<TimetableSlot> clashing = slotRepo.findBySchoolIdAndDayIdxAndSlotIdxAndTeacherId(
                    schoolId, in.dayIdx(), in.slotIdx(), in.teacherId());
            for (TimetableSlot other : clashing) {
                if (other.getClassId().equals(cls.getId())) {
                    continue; // same class — this is the slot being edited, not a conflict
                }
                String otherName = classRepo.findById(other.getClassId())
                        .map(SchoolClass::getName)
                        .orElse(null);
                conflicts.add(new Conflict(otherName, other.getDayIdx(), other.getSlotIdx(), in.teacherId()));
            }
        }

        // Upsert: reuse the existing slot for this cell, else create a new one.
        TimetableSlot slot = slotRepo
                .findBySchoolIdAndClassIdAndDayIdxAndSlotIdx(schoolId, cls.getId(), in.dayIdx(), in.slotIdx())
                .orElseGet(() -> {
                    TimetableSlot s = new TimetableSlot();
                    s.setSchoolId(schoolId);
                    s.setClassId(cls.getId());
                    s.setDayIdx(in.dayIdx());
                    s.setSlotIdx(in.slotIdx());
                    return s;
                });
        slot.setSubjectCode(in.subjectCode());
        slot.setTeacherId(in.teacherId());
        slot.setRoom(in.room());

        SlotView saved = toView(slotRepo.save(slot));
        // Slot is saved regardless; conflicts are returned as warnings, not rejections.
        return new SlotSaveResult(saved, conflicts);
    }

    @Transactional
    public void deleteSlot(String className, int dayIdx, int slotIdx) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = findClass(schoolId, className);
        slotRepo.findBySchoolIdAndClassIdAndDayIdxAndSlotIdx(schoolId, cls.getId(), dayIdx, slotIdx)
                .ifPresent(slotRepo::delete);
    }

    private SchoolClass findClass(UUID schoolId, String className) {
        return classRepo.findBySchoolIdAndName(schoolId, className)
                .orElseThrow(() -> ApiException.notFound("Classe"));
    }

    private ClassRef toRef(SchoolClass c) {
        return new ClassRef(c.getId(), c.getName(), c.getSectionId(), c.getSubsystem(), c.getLevel());
    }

    private SlotView toView(TimetableSlot s) {
        return new SlotView(s.getId(), s.getDayIdx(), s.getSlotIdx(),
                s.getSubjectCode(), s.getTeacherId(), s.getRoom());
    }
}
