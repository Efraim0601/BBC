package com.bbc.sms.guardian;

import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AuthorizationPolicyService;
import com.bbc.sms.platform.security.PolicyResourceContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRegistrationService;
import com.bbc.sms.student.StudentRegistrationService.*;
import com.bbc.sms.student.dto.StudentDtos.StudentUpsert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static com.bbc.sms.guardian.GuardianDtos.*;

@Service
public class FamilyImportService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final StudentRegistrationService registrations;
    private final AuthorizationPolicyService policy;
    public FamilyImportService(JdbcTemplate jdbc,ObjectMapper json,StudentRegistrationService registrations,AuthorizationPolicyService policy){this.jdbc=jdbc;this.json=json;this.registrations=registrations;this.policy=policy;}

    @Transactional
    public FamilyImportView dryRun(FamilyImportRequest req){
        requireImport();
        UUID job=UUID.randomUUID(),school=TenantContext.get(); Set<String> keys=new HashSet<>(); List<FamilyImportRowView> views=new ArrayList<>();int valid=0,rowNo=0;
        jdbc.update("INSERT INTO family_import_job(id,school_id,source_name,total_rows,status) VALUES (?,?,?,?,'DRAFT')",job,school,req.sourceName(),req.rows().size());
        for(FamilyImportRow row:req.rows()){
            rowNo++;String outcome="VALID",message="Prêt à importer";
            if(!keys.add(row.externalKey())){outcome="ERROR";message="Clé de ligne dupliquée dans le fichier";}
            else if(row.classId()==null){outcome="ERROR";message="Classe obligatoire";}
            else if(row.guardians()==null||row.guardians().isEmpty()){outcome="ERROR";message="Au moins un parent ou tuteur est obligatoire";}
            else if(row.guardians().stream().anyMatch(g->!"NO_PORTAL".equalsIgnoreCase(g.accessMode())&&(g.email()==null||g.email().isBlank()))){outcome="ERROR";message="E-mail obligatoire pour chaque parent qui doit accéder au portail";}
            if("VALID".equals(outcome))valid++;
            try{jdbc.update("INSERT INTO family_import_row(id,school_id,job_id,row_number,external_key,payload,status,message) VALUES (?,?,?,?,?,?::jsonb,?,?)",UUID.randomUUID(),school,job,rowNo,row.externalKey(),json.writeValueAsString(row),outcome,message);}catch(Exception e){throw ApiException.badRequest("Ligne d’import illisible");}
            views.add(new FamilyImportRowView(rowNo,row.externalKey(),row.lastName()+" "+row.firstName(),outcome,message));
        }
        jdbc.update("UPDATE family_import_job SET status='VALIDATED',valid_rows=? WHERE id=?",valid,job);
        return new FamilyImportView(job,"VALIDATED",req.rows().size(),valid,0,0,req.rows().size()-valid,views);
    }

    @Transactional
    public FamilyImportView commit(UUID jobId){
        requireImport();
        UUID school=TenantContext.get();String status=jdbc.query("SELECT status FROM family_import_job WHERE id=? AND school_id=?",rs->rs.next()?rs.getString(1):null,jobId,school);
        if(status==null)throw ApiException.notFound("Import");if(!"VALIDATED".equals(status)&&!"COMPLETED_ERRORS".equals(status))throw ApiException.conflict("Cet import ne peut pas être relancé dans son état actuel");
        jdbc.update("UPDATE family_import_job SET status='RUNNING' WHERE id=?",jobId);
        List<RowPayload> rows=jdbc.query("SELECT id,row_number,external_key,payload::text,status FROM family_import_row WHERE job_id=? AND school_id=? ORDER BY row_number",(rs,i)->new RowPayload((UUID)rs.getObject(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getString(5)),jobId,school);
        int created=0,linked=0,failed=0;
        for(RowPayload rp:rows){
            if("COMMITTED".equals(rp.status())||"ERROR".equals(rp.status())){if("ERROR".equals(rp.status()))failed++;continue;}
            try{
                FamilyImportRow row=json.readValue(rp.payload(),FamilyImportRow.class);
                List<GuardianInput> gs=row.guardians().stream().map(g->new GuardianInput(null,g.displayName(),g.email(),g.phone(),g.relationshipType()==null?"GUARDIAN":g.relationshipType(),g.accessMode()==null?"SEND_INVITE":g.accessMode(),null,true,false,null,true,false,true,true,false,true,false,true,"Import "+rp.externalKey())).toList();
                StudentUpsert s=new StudentUpsert(
                    row.firstName(), row.lastName(), row.niu(), row.sex(), row.dob(), row.birthplace(), Boolean.TRUE.equals(row.repeats()), row.classId(),
                    null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);
                RegistrationView result=registrations.register(new RegistrationRequest(s,gs));created++;linked+=result.guardians().size();
                jdbc.update("UPDATE family_import_row SET status='COMMITTED',message='Élève et famille créés',student_id=? WHERE id=?",result.student().id(),rp.id());
            }catch(Exception e){failed++;jdbc.update("UPDATE family_import_row SET status='ERROR',message=? WHERE id=?",safe(e.getMessage()),rp.id());}
        }
        String finalStatus=failed==0?"COMPLETED":"COMPLETED_ERRORS";
        jdbc.update("UPDATE family_import_job SET status=?,created_rows=?,linked_guardians=?,failed_rows=?,completed_at=? WHERE id=?",finalStatus,created,linked,failed,OffsetDateTime.now(),jobId);
        return view(jobId);
    }

    @Transactional(readOnly=true)
    public FamilyImportView view(UUID id){
        var head=jdbc.queryForMap("SELECT status,total_rows,valid_rows,created_rows,linked_guardians,failed_rows FROM family_import_job WHERE id=? AND school_id=?",id,TenantContext.get());
        List<FamilyImportRowView> rows=jdbc.query("SELECT row_number,external_key,payload->>'lastName' last_name,payload->>'firstName' first_name,status,message FROM family_import_row WHERE job_id=? ORDER BY row_number",(rs,i)->new FamilyImportRowView(rs.getInt(1),rs.getString(2),rs.getString(3)+" "+rs.getString(4),rs.getString(5),rs.getString(6)),id);
        return new FamilyImportView(id,(String)head.get("status"),(Integer)head.get("total_rows"),(Integer)head.get("valid_rows"),(Integer)head.get("created_rows"),(Integer)head.get("linked_guardians"),(Integer)head.get("failed_rows"),rows);
    }
    private static String safe(String s){return s==null?"Erreur non précisée":s.substring(0,Math.min(1000,s.length()));}
    private void requireImport(){
        policy.require("STUDENT_IMPORT", new PolicyResourceContext(TenantContext.get(), null,
                java.time.LocalDate.now(), null, null, null, null, null, null, null, null, null));
    }
    private record RowPayload(UUID id,int rowNumber,String externalKey,String payload,String status){}
}
