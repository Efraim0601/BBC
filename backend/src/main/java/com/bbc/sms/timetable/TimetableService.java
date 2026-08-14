package com.bbc.sms.timetable;

import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.setup.SetupService;
import com.bbc.sms.staff.Employee;
import com.bbc.sms.staff.EmployeeRepository;
import com.bbc.sms.timetable.dto.TimetableDtos.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Collectors;

@Service
public class TimetableService {
    private final SchoolClassRepository classRepo;
    private final TimetableSlotRepository slotRepo;
    private final EmployeeRepository employees;
    private final TeacherScopeService teacherScope;
    private final SetupService setup;
    private final AcademicSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final TeachingAssignmentResolver assignments;
    private final TimetableVersionService versions;
    private final AuthorizationPolicyService policy;

    public TimetableService(SchoolClassRepository classRepo, TimetableSlotRepository slotRepo,
                            EmployeeRepository employees, TeacherScopeService teacherScope,
                            SetupService setup, AcademicSessionRepository sessions, JdbcTemplate jdbc,
                            TeachingAssignmentResolver assignments, TimetableVersionService versions,
                            AuthorizationPolicyService policy) {
        this.classRepo=classRepo; this.slotRepo=slotRepo; this.employees=employees;
        this.teacherScope=teacherScope; this.setup=setup; this.sessions=sessions; this.jdbc=jdbc;
        this.assignments=assignments; this.versions=versions; this.policy=policy;
    }

    @Transactional
    public List<ClassRef> classes() {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession(); UUID sessionId=academic.getId();
        Set<UUID> allowed=teacherScope.allowedClassIds();
        return classRepo.findBySchoolIdOrderByName(schoolId).stream()
            .filter(c->allowed==null||allowed.contains(c.getId()))
            .filter(c -> policy.decide("TIMETABLE_CLASS_SCHEDULE_VIEW", classContext(sessionId, c.getId())).allowed())
            .map(c->toRef(c,sessionId)).toList();
    }

    /**
     * Returns the session/class/subject teacher assignments used by the timetable.
     * Primary classes always resolve to the configured homeroom teacher. Secondary
     * classes resolve to the active responsible teacher in Class subjects.
     */
    @Transactional(readOnly=true)
    public List<SubjectTeacherView> subjectTeachers(UUID classId) {
        UUID schoolId=TenantContext.get(); UUID sessionId=currentSession().getId();
        SchoolClass cls=requireClass(schoolId,classId); teacherScope.assertClass(classId);
        policy.require("TIMETABLE_CLASS_SCHEDULE_VIEW", classContext(sessionId, classId));
        List<String> subjectCodes=jdbc.query("""
            SELECT s.code
              FROM academic_curriculum_subject cs
              JOIN subject s ON s.id=cs.subject_id
             WHERE cs.school_id=? AND cs.academic_session_id=? AND cs.class_id=?
             ORDER BY cs.display_order,s.code
            """,(rs,n)->rs.getString(1),schoolId,sessionId,classId);
        return subjectCodes.stream().map(code->resolveSubjectTeacher(cls, currentSession(), code)).toList();
    }

    @Transactional(readOnly=true)
    public List<PeriodView> periods() {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        return jdbc.query("SELECT id,slot_idx,label,start_time,end_time,active FROM timetable_period WHERE school_id=? AND active ORDER BY slot_idx",
            (rs,n)->new PeriodView(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),
                rs.getTime(4).toLocalTime().toString(),rs.getTime(5).toLocalTime().toString(),rs.getBoolean(6)),TenantContext.get());
    }

    @Transactional
    public PeriodView updatePeriod(int slotIdx, PeriodRequest in) {
        policy.require("TIMETABLE_DRAFT", schoolContext());
        if(slotIdx<0||slotIdx>15) throw ApiException.badRequest("Numéro de période invalide");
        LocalTime start,end;
        try { start=LocalTime.parse(in.startTime()); end=LocalTime.parse(in.endTime()); }
        catch(Exception e){ throw ApiException.badRequest("Utilisez le format horaire HH:mm"); }
        if(!end.isAfter(start)) throw ApiException.badRequest("L'heure de fin doit être après l'heure de début");
        Integer overlaps=jdbc.queryForObject("""
          SELECT count(*) FROM timetable_period WHERE school_id=? AND slot_idx<>? AND active
           AND start_time < ?::time AND end_time > ?::time
          """,Integer.class,TenantContext.get(),slotIdx,end.toString(),start.toString());
        if(overlaps!=null&&overlaps>0) throw ApiException.conflict("Cette plage horaire chevauche une autre période configurée");
        UUID id=jdbc.queryForObject("""
          INSERT INTO timetable_period(school_id,slot_idx,label,start_time,end_time,active)
          VALUES (?,?,?,?,?,?) ON CONFLICT(school_id,slot_idx) DO UPDATE SET
          label=excluded.label,start_time=excluded.start_time,end_time=excluded.end_time,active=excluded.active RETURNING id
          """,UUID.class,TenantContext.get(),slotIdx,in.label().trim(),start,end,in.active());
        return periods().stream().filter(p->p.id().equals(id)).findFirst()
            .orElse(new PeriodView(id,slotIdx,in.label().trim(),start.toString(),end.toString(),in.active()));
    }

    @Transactional(readOnly=true)
    public List<SlotView> grid(String className) {
        return grid(className, null);
    }

    @Transactional(readOnly=true)
    public List<SlotView> grid(String className, UUID requestedVersionId) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=findClass(schoolId,className); teacherScope.assertClass(cls.getId());
        policy.require("TIMETABLE_CLASS_SCHEDULE_VIEW", classContext(academic.getId(), cls.getId()));
        UUID versionId = requestedVersionId == null ? versions.versionForClass(academic.getId(), cls.getId()) : requestedVersionId;
        if (versionId != null) {
            Integer owned = jdbc.queryForObject("SELECT count(*) FROM timetable_version WHERE id=? AND school_id=? AND academic_session_id=?",
                    Integer.class, versionId, schoolId, academic.getId());
            if (owned == null || owned == 0) throw ApiException.badRequest("La version du planning n'appartient pas à la session active.");
        }
        List<TimetableSlot> sourceSlots = versionId == null
                ? slotRepo.findBySchoolIdAndAcademicSessionIdAndClassId(schoolId,academic.getId(),cls.getId())
                : slotRepo.findBySchoolIdAndAcademicSessionIdAndTimetableVersionIdAndClassId(schoolId,academic.getId(),versionId,cls.getId());
        return sourceSlots.stream()
            .sorted(Comparator.comparingInt(TimetableSlot::getDayIdx).thenComparingInt(TimetableSlot::getSlotIdx))
            .map(s->toEffectiveView(s,cls,academic.getId())).toList();
    }

    @Transactional(readOnly=true)
    public List<String> rooms() {
        policy.require("TIMETABLE_ROOM_VIEW", schoolContext());
        return slotRepo.findDistinctRooms(TenantContext.get());
    }

    @Transactional(readOnly=true)
    public List<TeacherConflict> conflicts() {
        policy.require("TIMETABLE_MASTER_VIEW", schoolContext());
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession(); UUID sessionId=academic.getId();
        Map<UUID,SchoolClass> classes=classRepo.findBySchoolIdOrderByName(schoolId).stream()
            .collect(Collectors.toMap(SchoolClass::getId,c->c));
        List<TimetableSlot> effective=slotRepo.findBySchoolIdAndAcademicSessionId(schoolId,sessionId).stream()
            .filter(s -> { UUID v = versions.versionForClass(sessionId, s.getClassId()); return v == null || Objects.equals(v, s.getTimetableVersionId()); })
            .map(s->effectiveSlot(s,classes.get(s.getClassId()),academic))
            .filter(s->s.getTeacherId()!=null).toList();
        return buildConflicts(schoolId,effective);
    }

    @Transactional
    public ClassRef configure(UUID classId, ClassConfigRequest in) {
        policy.require("TIMETABLE_DRAFT", classContext(currentSession().getId(), classId));
        throw ApiException.coded(org.springframework.http.HttpStatus.GONE, "ASSIGNMENTS_MANAGED_IN_ACADEMIC_SETUP",
                "Les affectations d'enseignants se gèrent dans Configuration académique. Le planning est un consommateur en lecture seule.");
        /*
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=requireClass(schoolId,classId); String model=model(cls);
        ensureDraft(classId,academic.getId());
        if ("HOMEROOM".equals(model) && in.homeroomTeacherId()==null)
            throw ApiException.badRequest("Sélectionnez l'enseignant titulaire de cette classe");
        if (in.homeroomTeacherId()!=null) {
            setup.bindTeacherSection(in.homeroomTeacherId(),cls.getLevel());
            jdbc.update("""
                UPDATE class_teacher_assignment SET status='INACTIVE', effective_to=?, version=version+1, updated_at=now()
                 WHERE school_id=? AND academic_session_id=? AND class_id=? AND role='HOMEROOM' AND status='ACTIVE'
                   AND employee_id<>?
                """, academic.getStartDate().minusDays(1), schoolId, academic.getId(), classId, in.homeroomTeacherId());
            jdbc.update("""
                INSERT INTO class_teacher_assignment
                    (school_id,academic_session_id,class_id,employee_id,role,effective_from,status,source)
                VALUES (?,?,?,?, 'HOMEROOM',?,'ACTIVE','CLASS_SUBJECTS')
                ON CONFLICT (school_id,academic_session_id,class_id,employee_id,role,effective_from)
                DO UPDATE SET status='ACTIVE',effective_to=NULL,version=class_teacher_assignment.version+1,updated_at=now()
                """, schoolId, academic.getId(), classId, in.homeroomTeacherId(), academic.getStartDate());
        }
        int changed=jdbc.update("""
            UPDATE timetable_class_config SET homeroom_teacher_id=?,version=version+1,updated_at=now()
             WHERE school_id=? AND academic_session_id=? AND class_id=? AND version=? AND status='DRAFT'
            """,in.homeroomTeacherId(),schoolId,academic.getId(),classId,in.version());
        if(changed==0) throw ApiException.conflict("La configuration a changé. Rechargez la page avant de réessayer.");
        return toRef(cls,academic.getId());
        */
    }

    @Transactional
    public void assignTeacher(UUID classId, UUID teacherId, TeacherAssignmentRequest in) {
        policy.require("TIMETABLE_DRAFT", classContext(currentSession().getId(), classId));
        throw ApiException.coded(org.springframework.http.HttpStatus.GONE, "ASSIGNMENTS_MANAGED_IN_ACADEMIC_SETUP",
                "Les enseignants sont gérés dans Paramètres → Scolarité → Matières par classe. Le planning est en lecture seule pour cette information.");
    }

    @Transactional
    public SlotSaveResult upsertSlot(SlotUpsert in) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=findClass(schoolId,in.className()); teacherScope.assertClass(cls.getId());
        policy.require("TIMETABLE_DRAFT", classContext(academic.getId(), cls.getId()));
        ensureConfig(cls,academic.getId());
        ensureDraft(cls.getId(),academic.getId());
        UUID timetableVersionId = versions.ensureDraftVersion(academic.getId());
        if(in.dayIdx()<0||in.dayIdx()>5) throw ApiException.badRequest("Jour invalide");
        if(!periodExists(in.slotIdx())) throw ApiException.badRequest("Cette période n'est pas configurée");
        if(in.subjectCode()==null||in.subjectCode().isBlank()) throw ApiException.badRequest("La matière est obligatoire");
        String subjectCode=in.subjectCode().trim().toUpperCase(Locale.ROOT);
        SubjectTeacherView assignment=resolveSubjectTeacher(cls,academic,subjectCode);
        validateTeachingModel(assignment,in.teacherId(),subjectCode);
        UUID teacherId=assignment.teacherId();
        setup.bindTeacherSection(teacherId,cls.getLevel());
        SlotUpsert canonical=new SlotUpsert(in.className(),in.dayIdx(),in.slotIdx(),subjectCode,teacherId,in.room());
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class,
                schoolId + ":timetable:" + academic.getId() + ":" + timetableVersionId + ":" + in.dayIdx() + ":" + in.slotIdx());
        versions.assertSlotResourcesAvailable(academic.getId(), timetableVersionId, cls.getId(), teacherId,
                in.dayIdx(), in.slotIdx(), in.room());
        assertNoEffectiveTeacherConflict(schoolId,academic.getId(),timetableVersionId,cls,canonical);
        assertNoRoomConflict(schoolId,academic.getId(),timetableVersionId,cls,canonical);

        TimetableSlot slot=slotRepo.findBySchoolIdAndAcademicSessionIdAndTimetableVersionIdAndClassIdAndDayIdxAndSlotIdx(
            schoolId,academic.getId(),timetableVersionId,cls.getId(),in.dayIdx(),in.slotIdx()).orElseGet(TimetableSlot::new);
        slot.setSchoolId(schoolId); slot.setAcademicSessionId(academic.getId()); slot.setClassId(cls.getId());
        slot.setTimetableVersionId(timetableVersionId);
        slot.setDayIdx(in.dayIdx()); slot.setSlotIdx(in.slotIdx()); slot.setSubjectCode(subjectCode);
        slot.setTeacherId(teacherId); slot.setRoom(trim(in.room()));
        slot.setAssignmentId(assignment.assignmentId());
        slot.setAssignmentVersion(assignment.assignmentVersion());
        try { slot=slotRepo.saveAndFlush(slot); }
        catch(DataIntegrityViolationException e){ throw ApiException.conflict("TIMETABLE_SLOT_CONFLICT",
                "Conflit de planning : cet enseignant ou cette salle est déjà occupé à cette heure.", List.of(
                        Map.of("resourceType", "SLOT", "class", cls.getName(), "dayIdx", in.dayIdx(),
                                "slotIdx", in.slotIdx(), "repair", "Choose another period, room, or canonical teacher.")));
        }
        jdbc.update("UPDATE timetable_class_config SET version=version+1,updated_at=now() WHERE school_id=? AND academic_session_id=? AND class_id=?",
            schoolId,academic.getId(),cls.getId());
        return new SlotSaveResult(toView(slot,cls.getName()),List.of());
    }

    @Transactional
    public void deleteSlot(String className,int dayIdx,int slotIdx) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=findClass(schoolId,className); teacherScope.assertClass(cls.getId());
        policy.require("TIMETABLE_DRAFT", classContext(academic.getId(), cls.getId()));
        ensureDraft(cls.getId(),academic.getId());
        UUID timetableVersionId = versions.versionForClass(academic.getId(), cls.getId());
        if (timetableVersionId == null) return;
        slotRepo.findBySchoolIdAndAcademicSessionIdAndTimetableVersionIdAndClassIdAndDayIdxAndSlotIdx(schoolId,academic.getId(),timetableVersionId,cls.getId(),dayIdx,slotIdx).ifPresent(slotRepo::delete);
    }

    @Transactional
    public ClassRef publish(UUID classId, PlanActionRequest in) {
        policy.require("TIMETABLE_PUBLISH", classContext(currentSession().getId(), classId));
        throw ApiException.coded(org.springframework.http.HttpStatus.GONE, "TIMETABLE_VERSION_REQUIRED",
                "Publiez une version complète depuis la bannière Version du planning.");
        /*
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession(); SchoolClass cls=requireClass(schoolId,classId);
        ClassRef current=toRef(cls,academic.getId());
        UUID timetableVersionId = versions.ensureDraftVersion(academic.getId());
        List<TimetableSlot> slots=slotRepo.findBySchoolIdAndAcademicSessionIdAndTimetableVersionIdAndClassId(schoolId,academic.getId(),timetableVersionId,classId);
        if(slots.isEmpty()) throw ApiException.badRequest("Ajoutez au moins un cours avant de publier l'emploi du temps");
        if("HOMEROOM".equals(current.model())&&current.homeroomTeacherId()==null)
            throw ApiException.badRequest("Définissez d'abord l'enseignant titulaire");
        for (TimetableSlot slot : slots) {
            if (slot.getSubjectCode()==null || slot.getSubjectCode().isBlank())
                throw ApiException.badRequest("Each timetable slot must have a subject before publication.");
            String code=slot.getSubjectCode().trim().toUpperCase(Locale.ROOT);
            SubjectTeacherView assignment=resolveSubjectTeacher(cls,academic,code);
            if (assignment.teacherId()==null)
                throw ApiException.badRequest("Cannot publish "+cls.getName()+": "+assignment.message());
            SlotUpsert canonical=new SlotUpsert(cls.getName(),slot.getDayIdx(),slot.getSlotIdx(),code,assignment.teacherId(),slot.getRoom());
            assertNoEffectiveTeacherConflict(schoolId,academic.getId(),timetableVersionId,cls,canonical);
            slot.setSubjectCode(code);
            slot.setTeacherId(assignment.teacherId());
            slot.setAssignmentId(assignment.assignmentId());
            slot.setAssignmentVersion(assignment.assignmentVersion());
            slot.setPublishedTeacherId(assignment.teacherId());
            slot.setPublishedAssignmentId(assignment.assignmentId());
            slot.setPublishedAssignmentVersion(assignment.assignmentVersion());
        }
        slotRepo.flush();
        if(slots.stream().anyMatch(s->s.getTeacherId()==null||s.getSubjectCode()==null||s.getSubjectCode().isBlank()))
            throw ApiException.badRequest("Chaque cours doit avoir une matière et un enseignant avant publication");
        int changed=jdbc.update("""
          UPDATE timetable_class_config SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1,updated_at=now()
           WHERE school_id=? AND academic_session_id=? AND class_id=? AND version=? AND status='DRAFT'
          """,actorId(),schoolId,academic.getId(),classId,in.version());
        if(changed==0) throw ApiException.conflict("Ce planning a déjà changé ou est déjà publié. Rechargez la page.");
        audit(classId,"TIMETABLE_PUBLISHED",in.reason()); return toRef(cls,academic.getId());
        */
    }

    @Transactional
    public ClassRef reopen(UUID classId, PlanActionRequest in) {
        policy.require("TIMETABLE_REOPEN", classContext(currentSession().getId(), classId));
        throw ApiException.coded(org.springframework.http.HttpStatus.GONE, "TIMETABLE_VERSION_REQUIRED",
                "Rouvrez une nouvelle version depuis la bannière Version du planning; l'historique publié reste immuable.");
        /*
        if(in.reason()==null||in.reason().isBlank()) throw ApiException.badRequest("Le motif de réouverture est obligatoire");
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession(); SchoolClass cls=requireClass(schoolId,classId);
        int changed=jdbc.update("""
          UPDATE timetable_class_config SET status='DRAFT',version=version+1,updated_at=now()
           WHERE school_id=? AND academic_session_id=? AND class_id=? AND version=? AND status='PUBLISHED'
          """,schoolId,academic.getId(),classId,in.version());
        if(changed==0) throw ApiException.conflict("Seul un planning publié et à jour peut être rouvert");
        audit(classId,"TIMETABLE_REOPENED",in.reason().trim()); return toRef(cls,academic.getId());
        */
    }

    @Transactional(readOnly=true)
    public TeacherSchedule mySchedule() {
        UUID employeeId=jdbc.query("SELECT employee_id FROM app_user WHERE id=? AND school_id=? AND active=true",
                rs->rs.next()?rs.getObject(1,UUID.class):null,actorId(),TenantContext.get());
        if(employeeId==null) throw ApiException.badRequest("Votre compte n'est associé à aucun enseignant");
        policy.require("TIMETABLE_MY_SCHEDULE_VIEW", selfContext(employeeId));
        return teacherScheduleInternal(employeeId);
    }

    @Transactional(readOnly=true)
    public TeacherSchedule teacherSchedule(UUID teacherId) {
        policy.require("TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL", schoolContext());
        return teacherScheduleInternal(teacherId);
    }

    private TeacherSchedule teacherScheduleInternal(UUID teacherId) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        Employee teacher=employees.findByIdAndSchoolId(teacherId,schoolId).orElseThrow(()->ApiException.notFound("Enseignant"));
        Map<UUID,String> names=classNames(schoolId);
        Map<UUID,SchoolClass> classes=classRepo.findBySchoolIdOrderByName(schoolId).stream()
            .collect(Collectors.toMap(SchoolClass::getId,c->c));
        List<SlotView> slots=new ArrayList<>();
        for (TimetableSlot slot : slotRepo.findBySchoolIdAndAcademicSessionId(schoolId,academic.getId())) {
            if (!isPublished(slot.getClassId(),academic.getId())) continue;
            UUID publishedVersion = versions.versionForClass(academic.getId(), slot.getClassId());
            if (publishedVersion != null && !Objects.equals(publishedVersion, slot.getTimetableVersionId())) continue;
            SchoolClass cls=classes.get(slot.getClassId());
            UUID effectiveTeacher=slot.getPublishedTeacherId()==null ? slot.getTeacherId() : slot.getPublishedTeacherId();
            if (teacherId.equals(effectiveTeacher)) {
                slots.add(new SlotView(slot.getId(),slot.getDayIdx(),slot.getSlotIdx(),slot.getSubjectCode(),
                    effectiveTeacher,slot.getRoom(),names.get(slot.getClassId())));
            }
        }
        slots.sort(Comparator.comparingInt(SlotView::dayIdx).thenComparingInt(SlotView::slotIdx));
        return new TeacherSchedule(teacherId,teacher.getName(),academic.getLabel(),slots);
    }

    private void validateTeachingModel(SubjectTeacherView assignment,UUID requestedTeacherId,String subjectCode) {
        if (assignment==null || assignment.teacherId()==null) {
            String message=assignment==null || assignment.message()==null
                ? "No teacher is assigned to the class subject "+subjectCode : assignment.message();
            String code=assignment==null || assignment.errorCode()==null ? "ASSIGNMENT_MISSING" : assignment.errorCode();
            throw ApiException.coded(org.springframework.http.HttpStatus.CONFLICT, code, message);
        }
        if (requestedTeacherId!=null && !assignment.teacherId().equals(requestedTeacherId)) {
            throw ApiException.badRequest("The teacher for "+subjectCode+" is managed by Class subjects and must be "+assignment.teacherName()+".");
        }
    }

    private void assertNoEffectiveTeacherConflict(UUID schoolId,UUID sessionId,UUID versionId,SchoolClass cls,SlotUpsert in) {
        Map<UUID,SchoolClass> classes=classRepo.findBySchoolIdOrderByName(schoolId).stream()
            .collect(Collectors.toMap(SchoolClass::getId,c->c));
        for (TimetableSlot slot : slotRepo.findBySchoolIdAndAcademicSessionId(schoolId,sessionId)) {
            if (versionId != null && !Objects.equals(versionId, slot.getTimetableVersionId())) continue;
            if (slot.getClassId().equals(cls.getId()) || slot.getDayIdx()!=in.dayIdx() || slot.getSlotIdx()!=in.slotIdx()) continue;
            UUID effectiveTeacher=teacherForSlot(slot, classes.get(slot.getClassId()), sessionId);
            if (in.teacherId().equals(effectiveTeacher)) {
                SchoolClass other=classes.get(slot.getClassId());
                throw ApiException.conflict("TIMETABLE_TEACHER_CONFLICT",
                    "Teacher unavailable: this teacher already teaches "+slot.getSubjectCode()+" in "+
                    (other==null ? "another class" : other.getName())+" at this time.", List.of(
                        Map.of("resourceType", "TEACHER", "teacherId", in.teacherId(),
                                "class", other==null ? "another class" : other.getName(),
                                "subjectCode", Objects.toString(slot.getSubjectCode(), ""), "dayIdx", in.dayIdx(),
                                "slotIdx", in.slotIdx(), "existingSlotId", slot.getId(),
                                "repair", "Move the slot or change the responsible assignment in Academic Setup.")));
            }
        }
    }

    private void assertNoRoomConflict(UUID schoolId,UUID sessionId,UUID versionId,SchoolClass cls,SlotUpsert in) {
        if(in.room()==null||in.room().isBlank()) return;
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT c.name,s.subject_code FROM timetable_slot s JOIN school_class c ON c.id=s.class_id
           WHERE s.school_id=? AND s.academic_session_id=? AND s.timetable_version_id=? AND s.day_idx=? AND s.slot_idx=? AND lower(s.room)=lower(?) AND s.class_id<>?
          """,schoolId,sessionId,versionId,in.dayIdx(),in.slotIdx(),in.room().trim(),cls.getId());
        if(!rows.isEmpty()) throw ApiException.conflict("TIMETABLE_ROOM_CONFLICT",
                "Salle indisponible : "+in.room().trim()+" est déjà utilisée par "+rows.getFirst().get("name")+" sur cette période.",
                List.of(Map.of("resourceType", "ROOM", "room", in.room().trim(), "class", rows.getFirst().get("name"),
                        "subjectCode", Objects.toString(rows.getFirst().get("subject_code"), ""), "dayIdx", in.dayIdx(),
                        "slotIdx", in.slotIdx(), "repair", "Choose another room or period.")));
    }

    private record TimetableConfig(String model, UUID homeroomTeacherId, String homeroomTeacherName) {}

    private SubjectTeacherView resolveSubjectTeacher(SchoolClass cls, AcademicSession session, String subjectCode) {
        TeachingAssignmentResolver.Resolution resolved = assignments.resolve(session.getId(), cls.getId(), subjectCode, session.getStartDate());
        return new SubjectTeacherView(resolved.subjectCode(), resolved.teacherId(), resolved.teacherName(),
                resolved.teacherCode(), resolved.source(), resolved.locked(), resolved.messageFr(),
                resolved.status(), resolved.code(), resolved.assignmentId(), resolved.assignmentVersion());
    }

    /** Compatibility reader retained for older callers; all timetable writes use the resolver above. */
    private SubjectTeacherView resolveSubjectTeacher(SchoolClass cls,UUID sessionId,String subjectCode) {
        String code=subjectCode.trim().toUpperCase(Locale.ROOT);
        UUID schoolId=TenantContext.get();
        Integer configured=jdbc.queryForObject("""
            SELECT count(*) FROM academic_curriculum_subject cs JOIN subject s ON s.id=cs.subject_id
             WHERE cs.school_id=? AND cs.academic_session_id=? AND cs.class_id=? AND upper(s.code)=upper(?)
            """,Integer.class,schoolId,sessionId,cls.getId(),code);
        if (configured==null || configured==0) {
            return new SubjectTeacherView(code,null,null,null,null,true,
                "Subject "+code+" is not assigned to this class in Academic setup > Class subjects.");
        }
        TimetableConfig config=readConfig(cls,sessionId);
        if ("HOMEROOM".equals(config.model())) {
            if (config.homeroomTeacherId()==null) {
                return new SubjectTeacherView(code,null,null,null,"HOMEROOM",true,
                    "Configure the homeroom teacher before scheduling this primary class.");
            }
            return new SubjectTeacherView(code,config.homeroomTeacherId(),config.homeroomTeacherName(),null,
                "HOMEROOM",true,"Inherited from the class homeroom teacher.");
        }
        return jdbc.query("""
            SELECT ast.employee_id,e.name,e.code,ast.source
              FROM academic_class_subject_teacher ast
              JOIN subject s ON s.id=ast.subject_id
              JOIN employee e ON e.id=ast.employee_id
             WHERE ast.school_id=? AND ast.academic_session_id=? AND ast.class_id=?
               AND upper(s.code)=upper(?) AND ast.active=true
             ORDER BY CASE ast.role WHEN 'RESPONSIBLE' THEN 0 WHEN 'HOMEROOM' THEN 1 ELSE 2 END,
                      ast.created_at LIMIT 1
            """,rs -> rs.next()
                ? new SubjectTeacherView(code,rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),true,
                    "Inherited from the responsible teacher assigned in Class subjects.")
                : new SubjectTeacherView(code,null,null,null,null,true,
                    "Assign a responsible teacher for "+code+" in Academic setup > Class subjects before scheduling it."),
            schoolId,sessionId,cls.getId(),code);
    }

    private UUID effectiveTeacherId(SchoolClass cls,UUID sessionId,String subjectCode,UUID storedTeacherId) {
        if (cls==null || subjectCode==null || subjectCode.isBlank()) return storedTeacherId;
        AcademicSession session=sessions.findByIdAndSchoolId(sessionId,TenantContext.get()).orElse(null);
        if (session==null) return storedTeacherId;
        SubjectTeacherView assignment=resolveSubjectTeacher(cls,session,subjectCode);
        return assignment.teacherId()==null ? storedTeacherId : assignment.teacherId();
    }

    private UUID teacherForSlot(TimetableSlot source, SchoolClass cls, UUID sessionId) {
        if (source.getPublishedTeacherId() != null) return source.getPublishedTeacherId();
        return effectiveTeacherId(cls, sessionId, source.getSubjectCode(), source.getTeacherId());
    }

    private TimetableSlot effectiveSlot(TimetableSlot source,SchoolClass cls,AcademicSession session) {
        TimetableSlot effective=new TimetableSlot();
        effective.setId(source.getId()); effective.setSchoolId(source.getSchoolId()); effective.setAcademicSessionId(source.getAcademicSessionId());
        effective.setClassId(source.getClassId()); effective.setDayIdx(source.getDayIdx()); effective.setSlotIdx(source.getSlotIdx());
        effective.setSubjectCode(source.getSubjectCode()); effective.setRoom(source.getRoom());
        effective.setTeacherId(teacherForSlot(source, cls, session.getId()));
        return effective;
    }

    private TimetableConfig readConfig(SchoolClass cls,UUID sessionId) {
        return jdbc.query("""
            SELECT x.model,x.homeroom_teacher_id,e.name
              FROM timetable_class_config x LEFT JOIN employee e ON e.id=x.homeroom_teacher_id
             WHERE x.school_id=? AND x.academic_session_id=? AND x.class_id=?
            """,rs -> rs.next()
                ? new TimetableConfig(rs.getString(1),rs.getObject(2,UUID.class),rs.getString(3))
                : new TimetableConfig(model(cls),null,null),TenantContext.get(),sessionId,cls.getId());
    }

    private ClassRef toRef(SchoolClass c,UUID sessionId) {
        ensureConfig(c,sessionId);
        return jdbc.queryForObject("""
          SELECT x.model,x.status,a.employee_id,e.name,x.version FROM timetable_class_config x
          LEFT JOIN LATERAL (
            SELECT employee_id FROM class_teacher_assignment a
             WHERE a.school_id=x.school_id AND a.academic_session_id=x.academic_session_id
               AND a.class_id=x.class_id AND a.role='HOMEROOM' AND a.status='ACTIVE'
             ORDER BY a.effective_from DESC,a.created_at DESC LIMIT 1
          ) a ON true
          LEFT JOIN employee e ON e.id=a.employee_id
          WHERE x.school_id=? AND x.academic_session_id=? AND x.class_id=?
          """,(rs,n)->new ClassRef(c.getId(),c.getName(),c.getSectionId(),c.getSubsystem(),c.getLevel(),
            rs.getString(1),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4),rs.getLong(5)),TenantContext.get(),sessionId,c.getId());
    }

    private void ensureConfig(SchoolClass c,UUID sessionId) {
        jdbc.update("""
          INSERT INTO timetable_class_config(school_id,academic_session_id,class_id,model)
          VALUES (?,?,?,?) ON CONFLICT DO NOTHING
          """,TenantContext.get(),sessionId,c.getId(),model(c));
    }
    private String model(SchoolClass c){return "secondary".equalsIgnoreCase(c.getLevel())?"DEPARTMENTAL":"HOMEROOM";}
    private void ensureDraft(UUID classId,UUID sessionId){
        String status=jdbc.queryForObject("SELECT status FROM timetable_class_config WHERE school_id=? AND academic_session_id=? AND class_id=?",String.class,TenantContext.get(),sessionId,classId);
        if("PUBLISHED".equals(status)) throw ApiException.conflict("Ce planning est publié et verrouillé. Rouvrez-le avec un motif avant de le modifier.");
    }
    private boolean periodExists(int idx){Integer n=jdbc.queryForObject("SELECT count(*) FROM timetable_period WHERE school_id=? AND slot_idx=? AND active",Integer.class,TenantContext.get(),idx);return n!=null&&n>0;}
    private boolean isPublished(UUID classId,UUID sessionId){Boolean b=jdbc.queryForObject("SELECT status='PUBLISHED' FROM timetable_class_config WHERE school_id=? AND academic_session_id=? AND class_id=?",Boolean.class,TenantContext.get(),sessionId,classId);return Boolean.TRUE.equals(b);}
    private AcademicSession currentSession(){return sessions.findBySchoolIdOrderByStartDateDesc(TenantContext.get()).stream().filter(AcademicSession::isCurrent).findFirst().orElseThrow(()->ApiException.badRequest("Aucune année scolaire active"));}
    private SchoolClass findClass(UUID schoolId,String name){return classRepo.findBySchoolIdAndName(schoolId,name).orElseThrow(()->ApiException.notFound("Classe"));}
    private SchoolClass requireClass(UUID schoolId,UUID id){return classRepo.findByIdAndSchoolId(id,schoolId).orElseThrow(()->ApiException.notFound("Classe"));}
    private Map<UUID,String> classNames(UUID schoolId){return classRepo.findBySchoolIdOrderByName(schoolId).stream().collect(Collectors.toMap(SchoolClass::getId,SchoolClass::getName));}
    private SlotView toView(TimetableSlot s,String className){return new SlotView(s.getId(),s.getDayIdx(),s.getSlotIdx(),s.getSubjectCode(),s.getTeacherId(),s.getRoom(),className);}
    private SlotView toEffectiveView(TimetableSlot s,SchoolClass cls,UUID sessionId){
        return new SlotView(s.getId(),s.getDayIdx(),s.getSlotIdx(),s.getSubjectCode(),
            teacherForSlot(s,cls,sessionId),s.getRoom(),cls.getName());
    }
    private List<TeacherConflict> buildConflicts(UUID schoolId,List<TimetableSlot> slots){
        Map<String,List<TimetableSlot>> groups=slots.stream().collect(Collectors.groupingBy(s->s.getDayIdx()+"|"+s.getSlotIdx()+"|"+s.getTeacherId()));
        Map<UUID,String> cn=classNames(schoolId),tn=employees.findBySchoolId(schoolId).stream().collect(Collectors.toMap(Employee::getId,Employee::getName));
        return groups.values().stream().filter(g->g.stream().map(TimetableSlot::getClassId).distinct().count()>1).map(g->{TimetableSlot f=g.getFirst();return new TeacherConflict(f.getDayIdx(),f.getSlotIdx(),f.getTeacherId(),tn.get(f.getTeacherId()),g.stream().map(s->new ConflictSlot(s.getClassId(),cn.get(s.getClassId()),s.getSubjectCode(),s.getRoom())).toList());}).toList();
    }
    private PolicyResourceContext schoolContext() {
        return new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                null, null, null, null, null, null, null, null);
    }
    private PolicyResourceContext classContext(UUID sessionId, UUID classId) {
        return new PolicyResourceContext(TenantContext.get(), sessionId, LocalDate.now(), ParcoursContext.get(),
                classId, null, null, null, null, null, null, null);
    }
    private PolicyResourceContext selfContext(UUID employeeId) {
        return new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(), ParcoursContext.get(),
                null, null, null, null, null, employeeId, null, null);
    }
    private UUID actorId(){Object p=Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).map(a->a.getPrincipal()).orElse(null);return p instanceof AppUserPrincipal u?u.userId():null;}
    private void audit(UUID classId,String action,String reason){jdbc.update("INSERT INTO audit_event(school_id,actor_user_id,actor_username,action,aggregate_type,aggregate_id,reason,after_data) VALUES (?,?,?,?,?,?,?,?::jsonb)",TenantContext.get(),actorId(),actorId()==null?"system":"user",action,"TIMETABLE",classId.toString(),trim(reason),"{}");}
    private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
}
