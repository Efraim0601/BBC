package com.bbc.sms.guardian;

import com.bbc.sms.foundation.audit.AuditService;
import com.bbc.sms.identity.AppUser;
import com.bbc.sms.identity.AppUserRepository;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.Student;
import com.bbc.sms.student.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import static com.bbc.sms.guardian.GuardianDtos.*;

@Service
public class GuardianService {
    private final JdbcTemplate jdbc;
    private final StudentRepository students;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final GuardianAccountService accounts;
    private final AuthorizationPolicyService policy;

    public GuardianService(JdbcTemplate jdbc, StudentRepository students, AppUserRepository users,
                           PasswordEncoder encoder, AuditService audit, GuardianAccountService accounts,
                           AuthorizationPolicyService policy) {
        this.jdbc=jdbc; this.students=students; this.users=users; this.encoder=encoder;
        this.audit=audit; this.accounts=accounts; this.policy=policy;
    }

    @Transactional(readOnly=true)
    public List<GuardianSearchView> search(String query) {
        policy.require("GUARDIAN_DIRECTORY_SEARCH", new PolicyResourceContext(
                TenantContext.get(), null, LocalDate.now(), null, null, null,
                null, null, null, null, null, null));
        String q=query==null?"":query.trim();
        if(q.length()<3) throw ApiException.badRequest("Saisissez au moins 3 caractères");
        UUID school=TenantContext.get(); String email=normalEmail(q), phone=normalPhone(q);
        String like="%"+q.toLowerCase(Locale.ROOT)+"%";
        return jdbc.query("""
            SELECT g.id,g.display_name,g.email,g.phone,g.status,
              (SELECT count(*) FROM student_guardian sg WHERE sg.guardian_id=g.id AND sg.effective_to IS NULL) children,
              (g.normalized_email=? OR g.normalized_phone=?) exact_match
            FROM guardian g WHERE g.school_id=? AND g.status<>'MERGED'
              AND (g.normalized_email=? OR g.normalized_phone=? OR lower(g.display_name) LIKE ?)
            ORDER BY exact_match DESC, lower(g.display_name) LIMIT 20
            """,(rs,i)->new GuardianSearchView((UUID)rs.getObject("id"),rs.getString("display_name"),
                maskEmail(rs.getString("email")),maskPhone(rs.getString("phone")),rs.getInt("children"),
                rs.getString("status"),rs.getBoolean("exact_match")),email,phone,school,email,phone,like);
    }

    @Transactional(readOnly=true)
    public List<GuardianRelationshipView> list(UUID studentId) {
        requireStudentAction(studentId, "GUARDIAN_VIEW");
        requireStudent(studentId);
        return jdbc.query("""
            SELECT sg.*,g.display_name,g.email,g.phone,g.status,
              CASE WHEN EXISTS(SELECT 1 FROM guardian_account_token t WHERE t.guardian_id=g.id AND t.token_type='INVITE' AND t.used_at IS NULL AND t.expires_at>now()) THEN 'PENDING' ELSE NULL END invitation_status
            FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id
            WHERE sg.school_id=? AND sg.student_id=? ORDER BY sg.effective_to NULLS FIRST, sg.emergency_priority NULLS LAST,g.display_name
            """,(rs,i)->map(rs),TenantContext.get(),studentId);
    }

    @Transactional
    public GuardianRelationshipView add(UUID studentId, GuardianInput in) {
        requireStudentAction(studentId, "GUARDIAN_LINK_MANAGE");
        return addAuthorized(studentId, in);
    }

    /**
     * Create the initial family link as part of the atomic student-registration
     * transaction.  This deliberately uses the student-create authority rather
     * than granting the caller ongoing family-management rights on every record.
     */
    @Transactional
    public GuardianRelationshipView addForRegistration(UUID studentId, GuardianInput in) {
        policy.require("STUDENT_PROFILE_CREATE",
                PolicyResourceContext.empty().forSchool(TenantContext.get()));
        return addAuthorized(studentId, in);
    }

    private GuardianRelationshipView addAuthorized(UUID studentId, GuardianInput in) {
        Student student=requireStudent(studentId); UUID school=TenantContext.get(); UUID guardianId=in.guardianId();
        if(guardianId==null){
            String ne=normalEmail(in.email());
            List<UUID> existing=ne==null?List.of():jdbc.query("SELECT id FROM guardian WHERE school_id=? AND normalized_email=? AND status<>'MERGED'",(rs,i)->(UUID)rs.getObject(1),school,ne);
            if(existing.size()>1) throw ApiException.conflict("Plusieurs parents correspondent : sélection explicite requise");
            guardianId=existing.isEmpty()?createGuardian(in):existing.getFirst();
        } else requireGuardian(guardianId);
        UUID relationshipId=link(student,guardianId,toRelationship(in));
        if(in.guardianId()==null) provisionAccess(guardianId,in);
        GuardianRelationshipView out=findRelationship(relationshipId);
        audit.record("GUARDIAN_LINKED","Student",studentId.toString(),null,out,"Relation familiale ajoutée");
        return out;
    }

    /** Add an email and optionally provision portal access for an existing link. */
    @Transactional
    public GuardianRelationshipView updatePortalAccess(UUID studentId, UUID guardianId,
                                                       GuardianPortalAccessInput in) {
        requireStudentAction(studentId, "GUARDIAN_LINK_MANAGE");
        UUID school = TenantContext.get();
        UUID relationshipId = jdbc.query("""
            SELECT id FROM student_guardian
            WHERE school_id=? AND student_id=? AND guardian_id=? AND effective_to IS NULL
            """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, school, studentId, guardianId);
        if (relationshipId == null) throw ApiException.notFound("Relation familiale");

        String mode = normalizeAccessMode(in.accessMode());
        String email = normalEmail(in.email());
        GuardianContact contact = guardianContact(guardianId);
        if ("NO_PORTAL".equals(mode)) {
            if (email != null) updateGuardianEmail(guardianId, email);
            jdbc.update("""
                UPDATE student_guardian SET portal_access=false,version=version+1
                WHERE id=? AND school_id=?
                """, relationshipId, school);
            if (contact.appUserId() == null) {
                jdbc.update("UPDATE guardian SET status='NO_PORTAL',updated_at=now() WHERE id=? AND school_id=?",
                        guardianId, school);
            }
        } else {
            if (email == null) throw ApiException.badRequest("E-mail requis pour l’accès portail");
            updateGuardianEmail(guardianId, email);
            jdbc.update("""
                UPDATE student_guardian SET portal_access=true,version=version+1
                WHERE id=? AND school_id=?
                """, relationshipId, school);
            provisionAccess(guardianId, contact.displayName(), email, in.initialPassword(), mode);
        }

        syncCompatibility(guardianId);
        GuardianRelationshipView out = findRelationship(relationshipId);
        audit.record("GUARDIAN_PORTAL_ACCESS_UPDATED", "StudentGuardian", relationshipId.toString(), null,
                out, "Accès portail parent mis à jour");
        return out;
    }

    @Transactional
    public GuardianRelationshipView update(UUID relationshipId, RelationshipUpsert in) {
        requireRelationshipAction(relationshipId, "GUARDIAN_LINK_MANAGE");
        GuardianRelationshipView before=findRelationship(relationshipId);
        if(in.version()!=null && before.version()!=in.version()) throw ApiException.conflict("Relation modifiée par un autre utilisateur");
        int n=jdbc.update("""
          UPDATE student_guardian SET relationship_type=?,legal_guardian=?,lives_with=?,emergency_priority=?,pickup_authorized=?,finance_responsible=?,
          receives_academic=?,receives_attendance=?,receives_finance=?,receives_discipline=?,receives_health=?,portal_access=?,effective_from=?,effective_to=?,notes=?,version=version+1
          WHERE id=? AND school_id=?
          """,in.relationshipType(),bool(in.legalGuardian(),true),bool(in.livesWith(),false),in.emergencyPriority(),bool(in.pickupAuthorized(),false),
          bool(in.financeResponsible(),false),bool(in.receivesAcademic(),true),bool(in.receivesAttendance(),true),bool(in.receivesFinance(),false),
          bool(in.receivesDiscipline(),true),bool(in.receivesHealth(),false),bool(in.portalAccess(),true),
          in.effectiveFrom()==null?LocalDate.now():in.effectiveFrom(),in.effectiveTo(),in.notes(),relationshipId,TenantContext.get());
        if(n==0) throw ApiException.notFound("Relation familiale");
        GuardianRelationshipView out=findRelationship(relationshipId);
        audit.record("GUARDIAN_RELATIONSHIP_UPDATED","StudentGuardian",relationshipId.toString(),before,out,"Permissions familiales modifiées");
        return out;
    }

    @Transactional
    public void end(UUID relationshipId,String reason){
        requireRelationshipAction(relationshipId, "GUARDIAN_LINK_MANAGE");
        GuardianRelationshipView before=findRelationship(relationshipId);
        jdbc.update("UPDATE student_guardian SET effective_to=?,portal_access=false,version=version+1 WHERE id=? AND school_id=?",LocalDate.now(),relationshipId,TenantContext.get());
        syncCompatibility(before.guardianId());
        accounts.deactivateIfOrphan(before.guardianId());
        audit.record("GUARDIAN_RELATIONSHIP_ENDED","StudentGuardian",relationshipId.toString(),before,null,reason);
    }

    @Transactional
    public GuardianSearchView merge(UUID sourceId,MergeRequest in){
        policy.require("GUARDIAN_DIRECTORY_MANAGE", new PolicyResourceContext(
                TenantContext.get(), null, LocalDate.now(), null, null, null,
                null, null, null, null, null, null));
        if(sourceId.equals(in.targetGuardianId())) throw ApiException.badRequest("Le parent source et cible doivent être différents");
        requireGuardian(sourceId); requireGuardian(in.targetGuardianId());
        jdbc.update("""
          INSERT INTO student_guardian(school_id,student_id,guardian_id,relationship_type,legal_guardian,lives_with,emergency_priority,pickup_authorized,finance_responsible,receives_academic,receives_attendance,receives_finance,receives_discipline,receives_health,portal_access,effective_from,effective_to,notes)
          SELECT school_id,student_id,?,relationship_type,legal_guardian,lives_with,emergency_priority,pickup_authorized,finance_responsible,receives_academic,receives_attendance,receives_finance,receives_discipline,receives_health,portal_access,effective_from,effective_to,notes
          FROM student_guardian WHERE guardian_id=? ON CONFLICT(school_id,student_id,guardian_id) DO NOTHING
          """,in.targetGuardianId(),sourceId);
        jdbc.update("DELETE FROM student_guardian WHERE guardian_id=?",sourceId);
        jdbc.update("UPDATE guardian SET status='MERGED',merged_into_id=?,updated_at=now() WHERE id=? AND school_id=?",in.targetGuardianId(),sourceId,TenantContext.get());
        audit.record("GUARDIAN_MERGED","Guardian",sourceId.toString(),Map.of("source",sourceId),Map.of("target",in.targetGuardianId()),in.reason());
        return searchById(in.targetGuardianId());
    }

    UUID createGuardian(GuardianInput in){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO guardian(id,school_id,display_name,email,normalized_email,phone,normalized_phone,status) VALUES (?,?,?,?,?,?,?,'NO_PORTAL')",
            id,TenantContext.get(),in.displayName().trim(),blank(in.email()),normalEmail(in.email()),blank(in.phone()),normalPhone(in.phone()));
        return id;
    }

    private void provisionAccess(UUID guardianId,GuardianInput in){
        provisionAccess(guardianId, in.displayName(), in.email(), in.initialPassword(), in.accessMode());
    }

    private void provisionAccess(UUID guardianId, String displayName, String rawEmail,
                                 String initialPassword, String rawMode){
        String mode=normalizeAccessMode(rawMode);
        if("NO_PORTAL".equals(mode)) return;
        String email=normalEmail(rawEmail); if(email==null) throw ApiException.badRequest("E-mail requis pour l’accès portail");
        updateGuardianEmail(guardianId, email);
        String password=initialPassword;
        if("CREATE_ACCOUNT".equals(mode)) validatePassword(password);
        if(password==null||password.isBlank()) password=randomPassword();
        AppUser user=users.findBySchoolIdAndNormalizedEmail(TenantContext.get(),email).orElse(null);
        if(user==null){
            user=new AppUser(); user.setSchoolId(TenantContext.get()); user.setUsername(email); user.setEmail(blank(rawEmail)); user.setNormalizedEmail(email);
            user.setPasswordHash(encoder.encode(password)); user.setDisplayName(displayName.trim()); user.setInitials(initials(displayName)); user.setRoleCode("parent");
            user.setMustChangePassword(!"CREATE_ACCOUNT".equals(mode)); user=users.saveAndFlush(user);
        } else if(!"parent".equals(user.getRoleCode())) throw ApiException.conflict("Cet e-mail appartient à un compte non-parent");
        jdbc.update("UPDATE guardian SET app_user_id=?,status=?,updated_at=now() WHERE id=? AND school_id=?",user.getId(),"SEND_INVITE".equals(mode)?"INVITED":"ACTIVE",guardianId,TenantContext.get());
        if("SEND_INVITE".equals(mode)) accounts.issueInvite(guardianId);
        syncCompatibility(guardianId);
    }

    private UUID link(Student student,UUID guardianId,RelationshipUpsert in){
        List<UUID> existing=jdbc.query("SELECT id FROM student_guardian WHERE school_id=? AND student_id=? AND guardian_id=?",(rs,i)->(UUID)rs.getObject(1),TenantContext.get(),student.getId(),guardianId);
        if(!existing.isEmpty()) throw ApiException.conflict("Ce parent est déjà lié à cet élève");
        UUID id=UUID.randomUUID();
        jdbc.update("""
          INSERT INTO student_guardian(id,school_id,student_id,guardian_id,relationship_type,legal_guardian,lives_with,emergency_priority,pickup_authorized,finance_responsible,receives_academic,receives_attendance,receives_finance,receives_discipline,receives_health,portal_access,effective_from,notes)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,id,TenantContext.get(),student.getId(),guardianId,in.relationshipType(),bool(in.legalGuardian(),true),bool(in.livesWith(),false),in.emergencyPriority(),
          bool(in.pickupAuthorized(),false),bool(in.financeResponsible(),false),bool(in.receivesAcademic(),true),bool(in.receivesAttendance(),true),
          bool(in.receivesFinance(),false),bool(in.receivesDiscipline(),true),bool(in.receivesHealth(),false),bool(in.portalAccess(),true),
          in.effectiveFrom()==null?LocalDate.now():in.effectiveFrom(),in.notes());
        syncCompatibility(guardianId);
        return id;
    }

    private RelationshipUpsert toRelationship(GuardianInput in){
        boolean portal = !"NO_PORTAL".equals(normalizeAccessMode(in.accessMode()))
                && normalEmail(in.email()) != null && bool(in.portalAccess(), true);
        return new RelationshipUpsert(in.relationshipType(),in.legalGuardian(),in.livesWith(),in.emergencyPriority(),in.pickupAuthorized(),in.financeResponsible(),in.receivesAcademic(),in.receivesAttendance(),in.receivesFinance(),in.receivesDiscipline(),in.receivesHealth(),portal,LocalDate.now(),null,in.notes(),null);
    }
    private GuardianRelationshipView findRelationship(UUID id){
        List<GuardianRelationshipView> rows=jdbc.query("""
          SELECT sg.*,g.display_name,g.email,g.phone,g.status,
          CASE WHEN EXISTS(SELECT 1 FROM guardian_account_token t WHERE t.guardian_id=g.id AND t.token_type='INVITE' AND t.used_at IS NULL AND t.expires_at>now()) THEN 'PENDING' ELSE NULL END invitation_status
          FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id WHERE sg.id=? AND sg.school_id=?
          """,(rs,i)->map(rs),id,TenantContext.get());
        if(rows.isEmpty()) throw ApiException.notFound("Relation familiale"); return rows.getFirst();
    }
    private GuardianRelationshipView map(java.sql.ResultSet rs)throws java.sql.SQLException{return new GuardianRelationshipView((UUID)rs.getObject("id"),(UUID)rs.getObject("guardian_id"),rs.getString("display_name"),rs.getString("email"),rs.getString("phone"),rs.getString("relationship_type"),rs.getBoolean("legal_guardian"),rs.getBoolean("lives_with"),(Integer)rs.getObject("emergency_priority"),rs.getBoolean("pickup_authorized"),rs.getBoolean("finance_responsible"),rs.getBoolean("receives_academic"),rs.getBoolean("receives_attendance"),rs.getBoolean("receives_finance"),rs.getBoolean("receives_discipline"),rs.getBoolean("receives_health"),rs.getBoolean("portal_access"),rs.getObject("effective_from",LocalDate.class),rs.getObject("effective_to",LocalDate.class),rs.getString("status"),rs.getString("invitation_status"),rs.getLong("version"));}
    private void requireStudentAction(UUID studentId, String action) {
        policy.require(action, new PolicyResourceContext(TenantContext.get(), null, LocalDate.now(),
                null, null, null, studentId, null, null, null, null, null));
    }
    private void requireRelationshipAction(UUID relationshipId, String action) {
        UUID studentId = jdbc.query("SELECT student_id FROM student_guardian WHERE id=? AND school_id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, relationshipId, TenantContext.get());
        if (studentId == null) throw ApiException.notFound("Relation familiale");
        requireStudentAction(studentId, action);
    }
    private Student requireStudent(UUID id){return students.findByIdAndSchoolId(id,TenantContext.get()).orElseThrow(()->ApiException.notFound("Élève"));}
    private void requireGuardian(UUID id){Integer n=jdbc.queryForObject("SELECT count(*) FROM guardian WHERE id=? AND school_id=? AND status<>'MERGED'",Integer.class,id,TenantContext.get());if(n==null||n==0)throw ApiException.notFound("Parent");}
    private GuardianContact guardianContact(UUID id){
        List<GuardianContact> rows=jdbc.query("SELECT app_user_id,email,display_name FROM guardian WHERE id=? AND school_id=? AND status<>'MERGED'",
                (rs,i)->new GuardianContact((UUID)rs.getObject("app_user_id"),rs.getString("email"),rs.getString("display_name")),id,TenantContext.get());
        if(rows.isEmpty()) throw ApiException.notFound("Parent");
        return rows.getFirst();
    }
    private void updateGuardianEmail(UUID guardianId,String rawEmail){
        String email=normalEmail(rawEmail); if(email==null) return;
        GuardianContact current=guardianContact(guardianId);
        if(current.appUserId()!=null && !email.equals(normalEmail(current.email())))
            throw ApiException.conflict("Le compte parent possède déjà une autre adresse e-mail");
        Integer duplicate=jdbc.queryForObject("""
            SELECT count(*) FROM guardian
            WHERE school_id=? AND normalized_email=? AND id<>? AND status<>'MERGED'
            """,Integer.class,TenantContext.get(),email,guardianId);
        if(duplicate!=null&&duplicate>0) throw ApiException.conflict("Cette adresse e-mail est déjà utilisée par un autre parent");
        jdbc.update("UPDATE guardian SET email=?,normalized_email=?,updated_at=now() WHERE id=? AND school_id=?",
                blank(rawEmail),email,guardianId,TenantContext.get());
    }
    private static String normalizeAccessMode(String raw){
        String mode=raw==null?"":raw.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("SEND_INVITE","CREATE_ACCOUNT","NO_PORTAL").contains(mode))
            throw ApiException.badRequest("Mode d’accès parent invalide");
        return mode;
    }
    private GuardianSearchView searchById(UUID id){return jdbc.queryForObject("SELECT g.id,g.display_name,g.email,g.phone,g.status,(SELECT count(*) FROM student_guardian sg WHERE sg.guardian_id=g.id AND sg.effective_to IS NULL) children FROM guardian g WHERE id=? AND school_id=?",(rs,i)->new GuardianSearchView((UUID)rs.getObject("id"),rs.getString("display_name"),maskEmail(rs.getString("email")),maskPhone(rs.getString("phone")),rs.getInt("children"),rs.getString("status"),true),id,TenantContext.get());}
    private void syncCompatibility(UUID guardianId){jdbc.update("""
      INSERT INTO parent_student(parent_user_id,student_id)
      SELECT g.app_user_id,sg.student_id FROM guardian g JOIN student_guardian sg ON sg.guardian_id=g.id
      WHERE g.id=? AND g.app_user_id IS NOT NULL AND sg.effective_to IS NULL AND sg.portal_access=true
      ON CONFLICT DO NOTHING
      """,guardianId);jdbc.update("DELETE FROM parent_student ps WHERE ps.parent_user_id=(SELECT app_user_id FROM guardian WHERE id=?) AND NOT EXISTS(SELECT 1 FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id WHERE g.app_user_id=ps.parent_user_id AND sg.student_id=ps.student_id AND sg.effective_to IS NULL AND sg.portal_access=true)",guardianId);}
    static String normalEmail(String v){v=blank(v);return v==null?null:v.toLowerCase(Locale.ROOT);}
    static String normalPhone(String v){v=blank(v);return v==null?null:v.replaceAll("[^0-9+]","");}
    static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    private static boolean bool(Boolean v,boolean d){return v==null?d:v;}
    private static void validatePassword(String p){if(p==null||p.length()<8||!p.matches(".*[A-Za-z].*")||!p.matches(".*\\d.*"))throw ApiException.badRequest("Le mot de passe doit contenir au moins 8 caractères, une lettre et un chiffre");}
    private static String randomPassword(){return UUID.randomUUID().toString()+new SecureRandom().nextInt(10);}
    private static String initials(String n){String[] p=n.trim().split("\\s+");return (p[0].substring(0,1)+(p.length>1?p[p.length-1].substring(0,1):"A")).toUpperCase();}
    private static String maskEmail(String e){if(e==null)return null;int at=e.indexOf('@');if(at<2)return "***";return e.substring(0,1)+"***"+e.substring(at);}
    private static String maskPhone(String p){if(p==null)return null;String n=normalPhone(p);return n.length()<4?"***":"***"+n.substring(n.length()-4);}
    private record GuardianContact(UUID appUserId,String email,String displayName){}
}
