package com.bbc.sms.guardian;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRegistrationService;
import com.bbc.sms.student.StudentRegistrationService.*;
import com.bbc.sms.student.dto.StudentDtos.StudentUpsert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.util.*;
import static com.bbc.sms.guardian.GuardianDtos.*;

@Service
public class FamilyImportService {
    private static final Logger log = LoggerFactory.getLogger(FamilyImportService.class);
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final StudentRegistrationService registrations;
    private final AuthorizationPolicyService policy;
    private final TeacherScopeService scope;
    private final Validator validator;
    private final TransactionTemplate rowTransaction;
    public FamilyImportService(JdbcTemplate jdbc,ObjectMapper json,StudentRegistrationService registrations,AuthorizationPolicyService policy,TeacherScopeService scope,Validator validator,PlatformTransactionManager transactionManager){
        this.jdbc=jdbc;this.json=json;this.registrations=registrations;this.policy=policy;this.scope=scope;this.validator=validator;
        this.rowTransaction=new TransactionTemplate(transactionManager);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public FamilyImportView dryRun(FamilyImportRequest req){
        requireImport();
        if(req.rows()==null||req.rows().isEmpty()||req.rows().stream().anyMatch(Objects::isNull))throw ApiException.badRequest("Ajoutez au moins une ligne valide");
        assertScope(req.rows(),false);
        UUID job=UUID.randomUUID(),school=TenantContext.get(); Set<String> keys=new HashSet<>(); List<FamilyImportRowView> views=new ArrayList<>();int valid=0,rowNo=0;
        jdbc.update("INSERT INTO family_import_job(id,school_id,source_name,total_rows,status) VALUES (?,?,?,?,'DRAFT')",job,school,req.sourceName(),req.rows().size());
        for(FamilyImportRow row:req.rows()){
            rowNo++;String outcome="VALID",message="Prêt à importer";
            String invalid=invalidRow(row);
            if(invalid!=null){outcome="ERROR";message=invalid;}
            else if(!keys.add(row.externalKey())){outcome="ERROR";message="Clé de ligne dupliquée dans le fichier";}
            else if(row.classId()==null){outcome="ERROR";message="Classe obligatoire";}
            else if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM school_class WHERE id=? AND school_id=?)",Boolean.class,row.classId(),school))){outcome="ERROR";message="Classe inconnue";}
            else if(row.guardians()==null||row.guardians().isEmpty()){outcome="ERROR";message="Au moins un parent ou tuteur est obligatoire";}
            else if(row.guardians().stream().anyMatch(g->!"NO_PORTAL".equalsIgnoreCase(g.accessMode())&&(g.email()==null||g.email().isBlank()))){outcome="ERROR";message="E-mail obligatoire pour chaque parent qui doit accéder au portail";}
            if("VALID".equals(outcome))valid++;
            String key=row.externalKey()==null||row.externalKey().isBlank()?"invalid-row-"+rowNo:row.externalKey();
            try{jdbc.update("INSERT INTO family_import_row(id,school_id,job_id,row_number,external_key,payload,status,message) VALUES (?,?,?,?,?,?::jsonb,?,?)",UUID.randomUUID(),school,job,rowNo,key,json.writeValueAsString(row),outcome,message);}catch(Exception e){log.warn("Family import row {} could not be persisted: {}",rowNo,e.getMessage(),e);throw ApiException.badRequest("Ligne d’import illisible");}
            views.add(new FamilyImportRowView(rowNo,row.externalKey(),row.lastName()+" "+row.firstName(),outcome,message));
        }
        jdbc.update("UPDATE family_import_job SET status='VALIDATED',valid_rows=? WHERE id=?",valid,job);
        return new FamilyImportView(job,"VALIDATED",req.rows().size(),valid,0,0,req.rows().size()-valid,views);
    }

    @Transactional
    public FamilyImportView commit(UUID jobId){
        requireImport();
        // Serialize retries for this job. Successful row transactions remain committed
        // even if another row fails or the client loses its response.
        UUID school=TenantContext.get();String status=jdbc.query("SELECT status FROM family_import_job WHERE id=? AND school_id=? FOR UPDATE",rs->rs.next()?rs.getString(1):null,jobId,school);
        if(status==null)throw ApiException.notFound("Import");
        List<RowPayload> rows=jdbc.query("SELECT id,row_number,external_key,payload::text,status FROM family_import_row WHERE job_id=? AND school_id=? ORDER BY row_number",(rs,i)->new RowPayload((UUID)rs.getObject(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getString(5)),jobId,school);
        assertScope(rows.stream().map(this::payload).toList(),true);
        if(!"VALIDATED".equals(status)&&!"COMPLETED_ERRORS".equals(status))throw ApiException.conflict("Cet import ne peut pas être relancé dans son état actuel");
        jdbc.update("UPDATE family_import_job SET status='RUNNING' WHERE id=?",jobId);
        for(RowPayload rp:rows){
            if("COMMITTED".equals(rp.status())||"ERROR".equals(rp.status()))continue;
            try{
              rowTransaction.executeWithoutResult(tx->{
                FamilyImportRow row=payload(rp);
                String invalid=invalidRow(row);if(invalid!=null)throw ApiException.badRequest(invalid);
                List<GuardianInput> gs=row.guardians().stream().map(g->new GuardianInput(null,g.displayName(),g.email(),g.phone(),g.relationshipType()==null?"GUARDIAN":g.relationshipType(),g.accessMode()==null?"SEND_INVITE":g.accessMode(),null,true,false,null,true,false,true,true,false,true,false,true,"Import "+rp.externalKey())).toList();
                StudentUpsert s=new StudentUpsert(
                    row.firstName(), row.lastName(), row.niu(), row.sex(), row.dob(), row.birthplace(), Boolean.TRUE.equals(row.repeats()), row.classId(),
                    null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null,
                    false);
                RegistrationView result=registrations.register(new RegistrationRequest(s,gs));
                jdbc.update("UPDATE family_import_row SET status='COMMITTED',message='Élève et famille créés',student_id=? WHERE id=?",result.student().id(),rp.id());
              });
            }catch(Exception e){jdbc.update("UPDATE family_import_row SET status='ERROR',message=? WHERE id=?",safe(e.getMessage()),rp.id());}
        }
        int created=jdbc.queryForObject("SELECT count(*) FROM family_import_row WHERE school_id=? AND job_id=? AND status='COMMITTED'",Integer.class,school,jobId);
        int failed=jdbc.queryForObject("SELECT count(*) FROM family_import_row WHERE school_id=? AND job_id=? AND status='ERROR'",Integer.class,school,jobId);
        int linked=jdbc.queryForObject("SELECT count(*) FROM student_guardian g JOIN family_import_row r ON r.student_id=g.student_id AND r.school_id=g.school_id WHERE r.school_id=? AND r.job_id=? AND r.status='COMMITTED'",Integer.class,school,jobId);
        String finalStatus=failed==0?"COMPLETED":"COMPLETED_ERRORS";
        jdbc.update("UPDATE family_import_job SET status=?,created_rows=?,linked_guardians=?,failed_rows=?,completed_at=? WHERE id=?",finalStatus,created,linked,failed,OffsetDateTime.now(),jobId);
        return view(jobId);
    }

    @Transactional(readOnly=true)
    public FamilyImportView view(UUID id){
        requireImport();
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM family_import_job WHERE id=? AND school_id=?)",Boolean.class,id,TenantContext.get())))throw ApiException.notFound("Import");
        List<FamilyImportRow> payloads=jdbc.query("SELECT payload::text FROM family_import_row WHERE job_id=? AND school_id=?",(rs,n)->payload(new RowPayload(null,0,null,rs.getString(1),null)),id,TenantContext.get());
        assertScope(payloads,true);
        var head=jdbc.queryForMap("SELECT status,total_rows,valid_rows,created_rows,linked_guardians,failed_rows FROM family_import_job WHERE id=? AND school_id=?",id,TenantContext.get());
        List<FamilyImportRowView> rows=jdbc.query("SELECT row_number,external_key,payload->>'lastName' last_name,payload->>'firstName' first_name,status,message FROM family_import_row WHERE job_id=? AND school_id=? ORDER BY row_number",(rs,i)->new FamilyImportRowView(rs.getInt(1),rs.getString(2),rs.getString(3)+" "+rs.getString(4),rs.getString(5),rs.getString(6)),id,TenantContext.get());
        return new FamilyImportView(id,(String)head.get("status"),(Integer)head.get("total_rows"),(Integer)head.get("valid_rows"),(Integer)head.get("created_rows"),(Integer)head.get("linked_guardians"),(Integer)head.get("failed_rows"),rows);
    }
    private FamilyImportRow payload(RowPayload row){try{return json.readValue(row.payload(),FamilyImportRow.class);}catch(Exception e){throw ApiException.badRequest("Ligne d’import illisible");}}
    private String invalidRow(FamilyImportRow row){return validator.validate(row).stream().map(v->v.getMessage()).sorted().findFirst().orElse(null);}
    private void assertScope(List<FamilyImportRow> rows,boolean hide){
        Set<UUID> allowed=scope.allowedClassIds();
        if(allowed!=null&&rows.stream().anyMatch(r->r.classId()!=null&&!allowed.contains(r.classId()))) {
            if(hide)throw ApiException.notFound("Import");
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"La classe ne relève pas de votre périmètre");
        }
    }
    private static String safe(String s){return s==null?"Erreur non précisée":s.substring(0,Math.min(1000,s.length()));}
    private void requireImport(){
        policy.require("STUDENT_IMPORT", new PolicyResourceContext(TenantContext.get(), null,
                java.time.LocalDate.now(), null, null, null, null, null, null, null, null, null));
    }
    private record RowPayload(UUID id,int rowNumber,String externalKey,String payload,String status){}
}
