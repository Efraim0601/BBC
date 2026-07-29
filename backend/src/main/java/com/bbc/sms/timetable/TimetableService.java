package com.bbc.sms.timetable;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import com.bbc.sms.timetable.dto.TimetableDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TimetableService {

    private final SchoolClassRepository classRepo;
    private final TimetableSlotRepository slotRepo;
    private final EmployeeRepository employees;
    private final TeacherScopeService teacherScope;
    private final SetupService setup;

    public TimetableService(SchoolClassRepository classRepo, TimetableSlotRepository slotRepo,
                            EmployeeRepository employees, TeacherScopeService teacherScope,
                            SetupService setup) {
        this.classRepo = classRepo;
        this.slotRepo = slotRepo;
        this.employees = employees;
        this.teacherScope = teacherScope;
        this.setup = setup;
    }

    @Transactional(readOnly = true)
    public List<ClassRef> classes() {
        UUID schoolId = TenantContext.get();
        Set<UUID> allowed = teacherScope.allowedClassIds();
        return classRepo.findBySchoolIdOrderByName(schoolId).stream()
                .filter(c -> allowed == null || allowed.contains(c.getId()))
                .map(this::toRef)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SlotView> grid(String className) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = findClass(schoolId, className);
        teacherScope.assertClass(cls.getId());
        return slotRepo.findBySchoolIdAndClassId(schoolId, cls.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> rooms() {
        return slotRepo.findDistinctRooms(TenantContext.get());
    }

    /**
     * Tous les chevauchements d'enseignant de l'établissement : un même professeur
     * placé dans plusieurs classes — donc plusieurs salles — au même jour/heure.
     *
     * <p>Recalculé à la demande plutôt que stocké : la grille tient en quelques
     * centaines de créneaux, et le résultat ne peut pas se désynchroniser.
     */
    @Transactional(readOnly = true)
    public List<TeacherConflict> conflicts() {
        UUID schoolId = TenantContext.get();
        return buildConflicts(schoolId, slotRepo.findBySchoolIdAndTeacherIdIsNotNull(schoolId));
    }

    @Transactional
    public SlotSaveResult upsertSlot(SlotUpsert in) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = findClass(schoolId, in.className());
        teacherScope.assertClass(cls.getId());
        // Un enseignant n'exerce que dans sa section : l'affectation d'un prof du
        // primaire sur une classe du secondaire est refusée ici aussi, pas
        // seulement depuis l'écran des classes.
        if (in.teacherId() != null) setup.bindTeacherSection(in.teacherId(), cls.getLevel());

        // Un enseignant ne peut pas être placé dans deux classes à la même heure :
        // l'enregistrement est refusé, sauf demande explicite (classes regroupées).
        List<TimetableSlot> clashing = clashingSlots(schoolId, cls.getId(), in.dayIdx(), in.slotIdx(), in.teacherId());
        if (!clashing.isEmpty() && !in.allowOverlap()) {
            throw ApiException.conflict(overlapMessage(schoolId, in.teacherId(), clashing));
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
        // Chevauchement forcé : on le renvoie pour qu'il reste affiché dans la grille.
        List<TeacherConflict> conflicts = clashing.isEmpty()
                ? List.of()
                : buildConflicts(schoolId, slotRepo.findBySchoolIdAndDayIdxAndSlotIdxAndTeacherId(
                        schoolId, in.dayIdx(), in.slotIdx(), in.teacherId()));
        return new SlotSaveResult(saved, conflicts);
    }

    /** Créneaux d'AUTRES classes occupant déjà cet enseignant à ce jour/heure. */
    private List<TimetableSlot> clashingSlots(UUID schoolId, UUID classId, int dayIdx, int slotIdx, UUID teacherId) {
        if (teacherId == null) return List.of();
        return slotRepo.findBySchoolIdAndDayIdxAndSlotIdxAndTeacherId(schoolId, dayIdx, slotIdx, teacherId).stream()
                .filter(s -> !s.getClassId().equals(classId))   // même classe = le créneau édité
                .toList();
    }

    /** Message d'erreur nommant l'enseignant et les cours qu'il assure déjà sur ce créneau. */
    private String overlapMessage(UUID schoolId, UUID teacherId, List<TimetableSlot> clashing) {
        Map<UUID, String> classNames = classNames(schoolId);
        String teacher = employees.findByIdAndSchoolId(teacherId, schoolId)
                .map(Employee::getName)
                .orElse("Cet enseignant");
        String where = clashing.stream().map(s -> {
            String cls = classNames.getOrDefault(s.getClassId(), "une autre classe");
            String detail = Stream.of(s.getSubjectCode(), s.getRoom() == null ? null : "salle " + s.getRoom())
                    .filter(x -> x != null && !x.isBlank())
                    .collect(Collectors.joining(", "));
            return detail.isEmpty() ? cls : cls + " (" + detail + ")";
        }).collect(Collectors.joining(" ; "));
        return teacher + " est déjà en cours sur ce créneau : " + where
                + ". Un enseignant ne peut pas être dans deux salles à la même heure.";
    }

    /** Regroupe des créneaux par (jour, heure, enseignant) et ne garde que les groupes à plusieurs classes. */
    private List<TeacherConflict> buildConflicts(UUID schoolId, List<TimetableSlot> slots) {
        Map<String, List<TimetableSlot>> groups = new LinkedHashMap<>();
        for (TimetableSlot s : slots) {
            if (s.getTeacherId() == null) continue;
            groups.computeIfAbsent(s.getDayIdx() + "|" + s.getSlotIdx() + "|" + s.getTeacherId(),
                    k -> new ArrayList<>()).add(s);
        }
        List<TeacherConflict> out = new ArrayList<>();
        if (groups.isEmpty()) return out;

        Map<UUID, String> classNames = classNames(schoolId);
        Map<UUID, String> teacherNames = employees.findBySchoolId(schoolId).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName, (a, b) -> a));

        for (List<TimetableSlot> group : groups.values()) {
            if (group.stream().map(TimetableSlot::getClassId).distinct().count() < 2) continue;
            TimetableSlot first = group.get(0);
            List<ConflictSlot> involved = group.stream()
                    .map(s -> new ConflictSlot(s.getClassId(), classNames.get(s.getClassId()),
                            s.getSubjectCode(), s.getRoom()))
                    .sorted(Comparator.comparing(c -> c.className() == null ? "" : c.className()))
                    .toList();
            out.add(new TeacherConflict(first.getDayIdx(), first.getSlotIdx(), first.getTeacherId(),
                    teacherNames.get(first.getTeacherId()), involved));
        }
        out.sort(Comparator.comparingInt(TeacherConflict::dayIdx).thenComparingInt(TeacherConflict::slotIdx));
        return out;
    }

    private Map<UUID, String> classNames(UUID schoolId) {
        return classRepo.findBySchoolIdOrderByName(schoolId).stream()
                .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName, (a, b) -> a));
    }

    @Transactional
    public void deleteSlot(String className, int dayIdx, int slotIdx) {
        UUID schoolId = TenantContext.get();
        SchoolClass cls = findClass(schoolId, className);
        teacherScope.assertClass(cls.getId());
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
