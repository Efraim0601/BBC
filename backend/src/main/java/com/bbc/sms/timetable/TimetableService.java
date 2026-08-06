package com.bbc.sms.timetable;

import com.bbc.sms.foundation.session.AcademicSession;
import com.bbc.sms.foundation.session.AcademicSessionRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.TeacherScopeService;
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

    public TimetableService(SchoolClassRepository classRepo, TimetableSlotRepository slotRepo,
                            EmployeeRepository employees, TeacherScopeService teacherScope,
                            SetupService setup, AcademicSessionRepository sessions, JdbcTemplate jdbc) {
        this.classRepo=classRepo; this.slotRepo=slotRepo; this.employees=employees;
        this.teacherScope=teacherScope; this.setup=setup; this.sessions=sessions; this.jdbc=jdbc;
    }

    @Transactional
    public List<ClassRef> classes() {
        UUID schoolId=TenantContext.get(), sessionId=currentSession().getId();
        Set<UUID> allowed=teacherScope.allowedClassIds();
        return classRepo.findBySchoolIdOrderByName(schoolId).stream()
            .filter(c->allowed==null||allowed.contains(c.getId())).map(c->toRef(c,sessionId)).toList();
    }

    @Transactional(readOnly=true)
    public List<PeriodView> periods() {
        return jdbc.query("SELECT id,slot_idx,label,start_time,end_time,active FROM timetable_period WHERE school_id=? AND active ORDER BY slot_idx",
            (rs,n)->new PeriodView(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),
                rs.getTime(4).toLocalTime().toString(),rs.getTime(5).toLocalTime().toString(),rs.getBoolean(6)),TenantContext.get());
    }

    @Transactional
    public PeriodView updatePeriod(int slotIdx, PeriodRequest in) {
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
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=findClass(schoolId,className); teacherScope.assertClass(cls.getId());
        return slotRepo.findBySchoolIdAndAcademicSessionIdAndClassId(schoolId,academic.getId(),cls.getId()).stream()
            .sorted(Comparator.comparingInt(TimetableSlot::getDayIdx).thenComparingInt(TimetableSlot::getSlotIdx))
            .map(s->toView(s,cls.getName())).toList();
    }

    @Transactional(readOnly=true)
    public List<String> rooms() { return slotRepo.findDistinctRooms(TenantContext.get()); }

    @Transactional(readOnly=true)
    public List<TeacherConflict> conflicts() {
        UUID schoolId=TenantContext.get(), sessionId=currentSession().getId();
        return buildConflicts(schoolId,slotRepo.findBySchoolIdAndAcademicSessionIdAndTeacherIdIsNotNull(schoolId,sessionId));
    }

    @Transactional
    public ClassRef configure(UUID classId, ClassConfigRequest in) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=requireClass(schoolId,classId); String model=model(cls);
        ensureDraft(classId,academic.getId());
        if ("HOMEROOM".equals(model) && in.homeroomTeacherId()==null)
            throw ApiException.badRequest("Sélectionnez l'enseignant titulaire de cette classe");
        if (in.homeroomTeacherId()!=null) {
            setup.bindTeacherSection(in.homeroomTeacherId(),cls.getLevel());
            jdbc.update("INSERT INTO teacher_class(employee_id,class_id) VALUES (?,?) ON CONFLICT DO NOTHING",in.homeroomTeacherId(),classId);
        }
        int changed=jdbc.update("""
            UPDATE timetable_class_config SET homeroom_teacher_id=?,version=version+1,updated_at=now()
             WHERE school_id=? AND academic_session_id=? AND class_id=? AND version=? AND status='DRAFT'
            """,in.homeroomTeacherId(),schoolId,academic.getId(),classId,in.version());
        if(changed==0) throw ApiException.conflict("La configuration a changé. Rechargez la page avant de réessayer.");
        return toRef(cls,academic.getId());
    }

    @Transactional
    public void assignTeacher(UUID classId, UUID teacherId, TeacherAssignmentRequest in) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=requireClass(schoolId,classId); ensureDraft(classId,academic.getId());
        if(!"DEPARTMENTAL".equals(model(cls))) throw ApiException.badRequest("Les affectations par matière concernent les classes secondaires");
        if(in.subjectCodes()==null||in.subjectCodes().isEmpty()) throw ApiException.badRequest("Sélectionnez au moins une matière");
        setup.bindTeacherSection(teacherId,cls.getLevel());
        jdbc.update("INSERT INTO teacher_class(employee_id,class_id) VALUES (?,?) ON CONFLICT DO NOTHING",teacherId,classId);
        for(String code:in.subjectCodes()) {
            Integer valid=jdbc.queryForObject("SELECT count(*) FROM subject WHERE school_id=? AND upper(code)=upper(?)",Integer.class,schoolId,code);
            if(valid==null||valid==0) throw ApiException.badRequest("Matière inconnue : "+code);
            jdbc.update("""
          INSERT INTO teacher_subject(employee_id,subject_id)
          SELECT ?,id FROM subject WHERE school_id=? AND upper(code)=upper(?) ON CONFLICT DO NOTHING
          """,teacherId,schoolId,code);
        }
        audit(classId,"TIMETABLE_TEACHER_ASSIGNED","teacher="+teacherId+", subjects="+String.join(",",in.subjectCodes()));
    }

    @Transactional
    public SlotSaveResult upsertSlot(SlotUpsert in) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=findClass(schoolId,in.className()); teacherScope.assertClass(cls.getId());
        ensureDraft(cls.getId(),academic.getId());
        if(in.dayIdx()<0||in.dayIdx()>5) throw ApiException.badRequest("Jour invalide");
        if(!periodExists(in.slotIdx())) throw ApiException.badRequest("Cette période n'est pas configurée");
        if(in.subjectCode()==null||in.subjectCode().isBlank()) throw ApiException.badRequest("La matière est obligatoire");
        if(in.teacherId()==null) throw ApiException.badRequest("L'enseignant est obligatoire");
        setup.bindTeacherSection(in.teacherId(),cls.getLevel());
        validateTeachingModel(cls,academic.getId(),in.teacherId(),in.subjectCode());
        assertNoTeacherConflict(schoolId,academic.getId(),cls,in);
        assertNoRoomConflict(schoolId,academic.getId(),cls,in);

        TimetableSlot slot=slotRepo.findBySchoolIdAndAcademicSessionIdAndClassIdAndDayIdxAndSlotIdx(
            schoolId,academic.getId(),cls.getId(),in.dayIdx(),in.slotIdx()).orElseGet(TimetableSlot::new);
        slot.setSchoolId(schoolId); slot.setAcademicSessionId(academic.getId()); slot.setClassId(cls.getId());
        slot.setDayIdx(in.dayIdx()); slot.setSlotIdx(in.slotIdx()); slot.setSubjectCode(in.subjectCode().trim().toUpperCase(Locale.ROOT));
        slot.setTeacherId(in.teacherId()); slot.setRoom(trim(in.room()));
        try { slot=slotRepo.saveAndFlush(slot); }
        catch(DataIntegrityViolationException e){ throw ApiException.conflict("Conflit de planning : cet enseignant ou cette salle est déjà occupé à cette heure."); }
        jdbc.update("UPDATE timetable_class_config SET version=version+1,updated_at=now() WHERE school_id=? AND academic_session_id=? AND class_id=?",
            schoolId,academic.getId(),cls.getId());
        return new SlotSaveResult(toView(slot,cls.getName()),List.of());
    }

    @Transactional
    public void deleteSlot(String className,int dayIdx,int slotIdx) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        SchoolClass cls=findClass(schoolId,className); teacherScope.assertClass(cls.getId()); ensureDraft(cls.getId(),academic.getId());
        slotRepo.findBySchoolIdAndAcademicSessionIdAndClassIdAndDayIdxAndSlotIdx(schoolId,academic.getId(),cls.getId(),dayIdx,slotIdx).ifPresent(slotRepo::delete);
    }

    @Transactional
    public ClassRef publish(UUID classId, PlanActionRequest in) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession(); SchoolClass cls=requireClass(schoolId,classId);
        ClassRef current=toRef(cls,academic.getId());
        List<TimetableSlot> slots=slotRepo.findBySchoolIdAndAcademicSessionIdAndClassId(schoolId,academic.getId(),classId);
        if(slots.isEmpty()) throw ApiException.badRequest("Ajoutez au moins un cours avant de publier l'emploi du temps");
        if("HOMEROOM".equals(current.model())&&current.homeroomTeacherId()==null)
            throw ApiException.badRequest("Définissez d'abord l'enseignant titulaire");
        if(slots.stream().anyMatch(s->s.getTeacherId()==null||s.getSubjectCode()==null||s.getSubjectCode().isBlank()))
            throw ApiException.badRequest("Chaque cours doit avoir une matière et un enseignant avant publication");
        int changed=jdbc.update("""
          UPDATE timetable_class_config SET status='PUBLISHED',published_at=now(),published_by=?,version=version+1,updated_at=now()
           WHERE school_id=? AND academic_session_id=? AND class_id=? AND version=? AND status='DRAFT'
          """,actorId(),schoolId,academic.getId(),classId,in.version());
        if(changed==0) throw ApiException.conflict("Ce planning a déjà changé ou est déjà publié. Rechargez la page.");
        audit(classId,"TIMETABLE_PUBLISHED",in.reason()); return toRef(cls,academic.getId());
    }

    @Transactional
    public ClassRef reopen(UUID classId, PlanActionRequest in) {
        if(in.reason()==null||in.reason().isBlank()) throw ApiException.badRequest("Le motif de réouverture est obligatoire");
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession(); SchoolClass cls=requireClass(schoolId,classId);
        int changed=jdbc.update("""
          UPDATE timetable_class_config SET status='DRAFT',version=version+1,updated_at=now()
           WHERE school_id=? AND academic_session_id=? AND class_id=? AND version=? AND status='PUBLISHED'
          """,schoolId,academic.getId(),classId,in.version());
        if(changed==0) throw ApiException.conflict("Seul un planning publié et à jour peut être rouvert");
        audit(classId,"TIMETABLE_REOPENED",in.reason().trim()); return toRef(cls,academic.getId());
    }

    @Transactional(readOnly=true)
    public TeacherSchedule mySchedule() {
        UUID employeeId=jdbc.query("SELECT employee_id FROM app_user WHERE id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,actorId());
        if(employeeId==null) throw ApiException.badRequest("Votre compte n'est associé à aucun enseignant");
        return teacherSchedule(employeeId);
    }

    @Transactional(readOnly=true)
    public TeacherSchedule teacherSchedule(UUID teacherId) {
        UUID schoolId=TenantContext.get(); AcademicSession academic=currentSession();
        Employee teacher=employees.findByIdAndSchoolId(teacherId,schoolId).orElseThrow(()->ApiException.notFound("Enseignant"));
        Map<UUID,String> names=classNames(schoolId);
        List<SlotView> slots=slotRepo.findBySchoolIdAndAcademicSessionIdAndTeacherIdOrderByDayIdxAscSlotIdxAsc(schoolId,academic.getId(),teacherId).stream()
            .filter(s->isPublished(s.getClassId(),academic.getId())).map(s->toView(s,names.get(s.getClassId()))).toList();
        return new TeacherSchedule(teacherId,teacher.getName(),academic.getLabel(),slots);
    }

    private void validateTeachingModel(SchoolClass cls,UUID sessionId,UUID teacherId,String subjectCode) {
        ClassRef config=toRef(cls,sessionId);
        if("HOMEROOM".equals(config.model())) {
            if(config.homeroomTeacherId()==null) throw ApiException.badRequest("Configurez l'enseignant titulaire avant d'ajouter les cours");
            if(!config.homeroomTeacherId().equals(teacherId)) throw ApiException.badRequest("Au primaire, tous les cours doivent être assurés par l'enseignant titulaire "+config.homeroomTeacherName());
            return;
        }
        Integer subjectOk=jdbc.query("SELECT 1 FROM teacher_subject ts JOIN subject s ON s.id=ts.subject_id WHERE ts.employee_id=? AND s.school_id=? AND upper(s.code)=upper(?)",
            rs->rs.next()?1:0,teacherId,TenantContext.get(),subjectCode);
        Integer classOk=jdbc.query("SELECT 1 FROM teacher_class WHERE employee_id=? AND class_id=?",rs->rs.next()?1:0,teacherId,cls.getId());
        if(subjectOk==0) throw ApiException.badRequest("Cet enseignant n'est pas affecté à la matière "+subjectCode);
        if(classOk==0) throw ApiException.badRequest("Cet enseignant n'est pas affecté à la classe "+cls.getName());
    }

    private void assertNoTeacherConflict(UUID schoolId,UUID sessionId,SchoolClass cls,SlotUpsert in) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT c.name,s.subject_code,s.room FROM timetable_slot s JOIN school_class c ON c.id=s.class_id
           WHERE s.school_id=? AND s.academic_session_id=? AND s.day_idx=? AND s.slot_idx=? AND s.teacher_id=? AND s.class_id<>?
          """,schoolId,sessionId,in.dayIdx(),in.slotIdx(),in.teacherId(),cls.getId());
        if(!rows.isEmpty()) throw ApiException.conflict("Enseignant indisponible : il assure déjà "+rows.getFirst().get("subject_code")+" en "+rows.getFirst().get("name")+" sur cette période.");
    }

    private void assertNoRoomConflict(UUID schoolId,UUID sessionId,SchoolClass cls,SlotUpsert in) {
        if(in.room()==null||in.room().isBlank()) return;
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT c.name,s.subject_code FROM timetable_slot s JOIN school_class c ON c.id=s.class_id
           WHERE s.school_id=? AND s.academic_session_id=? AND s.day_idx=? AND s.slot_idx=? AND lower(s.room)=lower(?) AND s.class_id<>?
          """,schoolId,sessionId,in.dayIdx(),in.slotIdx(),in.room().trim(),cls.getId());
        if(!rows.isEmpty()) throw ApiException.conflict("Salle indisponible : "+in.room().trim()+" est déjà utilisée par "+rows.getFirst().get("name")+" sur cette période.");
    }

    private ClassRef toRef(SchoolClass c,UUID sessionId) {
        ensureConfig(c,sessionId);
        return jdbc.queryForObject("""
          SELECT x.model,x.status,x.homeroom_teacher_id,e.name,x.version FROM timetable_class_config x
          LEFT JOIN employee e ON e.id=x.homeroom_teacher_id
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
    private List<TeacherConflict> buildConflicts(UUID schoolId,List<TimetableSlot> slots){
        Map<String,List<TimetableSlot>> groups=slots.stream().collect(Collectors.groupingBy(s->s.getDayIdx()+"|"+s.getSlotIdx()+"|"+s.getTeacherId()));
        Map<UUID,String> cn=classNames(schoolId),tn=employees.findBySchoolId(schoolId).stream().collect(Collectors.toMap(Employee::getId,Employee::getName));
        return groups.values().stream().filter(g->g.stream().map(TimetableSlot::getClassId).distinct().count()>1).map(g->{TimetableSlot f=g.getFirst();return new TeacherConflict(f.getDayIdx(),f.getSlotIdx(),f.getTeacherId(),tn.get(f.getTeacherId()),g.stream().map(s->new ConflictSlot(s.getClassId(),cn.get(s.getClassId()),s.getSubjectCode(),s.getRoom())).toList());}).toList();
    }
    private UUID actorId(){Object p=Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication()).map(a->a.getPrincipal()).orElse(null);return p instanceof AppUserPrincipal u?u.userId():null;}
    private void audit(UUID classId,String action,String reason){jdbc.update("INSERT INTO audit_event(school_id,actor_user_id,actor_username,action,aggregate_type,aggregate_id,reason,after_data) VALUES (?,?,?,?,?,?,?,?::jsonb)",TenantContext.get(),actorId(),actorId()==null?"system":"user",action,"TIMETABLE",classId.toString(),trim(reason),"{}");}
    private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
}
