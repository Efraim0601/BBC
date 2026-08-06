package com.bbc.sms.guardian;

import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.foundation.session.SessionDtos.SessionUpsert;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRegistrationService;
import com.bbc.sms.student.StudentRegistrationService.RegistrationRequest;
import com.bbc.sms.student.dto.StudentDtos.StudentUpsert;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDate;
import java.util.*;
import static com.bbc.sms.guardian.GuardianDtos.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties={"bbc.bootstrap.enabled=false"})
class FamilyManagementIntegrationTest {
 @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("family").withUsername("bbc").withPassword("bbc");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);}
 @Autowired JdbcTemplate jdbc;@Autowired AcademicSessionService sessions;@Autowired StudentRegistrationService registrations;@Autowired GuardianService guardians;@Autowired FamilyImportService imports;
 UUID school,classId;
 @BeforeEach void setup(){school=UUID.randomUUID();classId=UUID.randomUUID();jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)",school,"F"+school.toString().substring(0,6),"Family school");jdbc.update("INSERT INTO role(code,label_fr,label_en) VALUES ('parent','Parent','Parent') ON CONFLICT DO NOTHING");String section="s"+school.toString().substring(0,8);jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,?,?)",section,school,"Primaire","FR","primary");jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,?,?,?)",classId,school,section,"CM1-"+school.toString().substring(0,4),"FR","primary");TenantContext.set(school);sessions.create(new SessionUpsert("2026-27","2026-2027",LocalDate.of(2026,8,1),LocalDate.of(2027,7,31),"OPEN",true,null,null,null,null,null));}
 @AfterEach void clear(){TenantContext.clear();}

 @Test void registrationIsAtomicAndExistingGuardianCanBeLinkedToSibling(){
  GuardianInput invalid=new GuardianInput(null,"Parent sans email",null,null,"MOTHER","SEND_INVITE",null,true,true,1,true,true,true,true,true,true,false,true,null);
  assertThatThrownBy(()->registrations.register(new RegistrationRequest(student("First","Child"),List.of(invalid))));
  assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isZero();
  GuardianInput parent=new GuardianInput(null,"Awa Parent","awa@example.test","+237 600 000 001","MOTHER","CREATE_ACCOUNT","Parent73x",true,true,1,true,true,true,true,true,true,false,true,null);
  var first=registrations.register(new RegistrationRequest(student("Amina","Nana"),List.of(parent)));
  assertThat(first.guardians()).hasSize(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM student_enrollment WHERE student_id=?",Integer.class,first.student().id())).isEqualTo(1);
  UUID guardianId=first.guardians().getFirst().guardianId();
  GuardianInput existing=new GuardianInput(guardianId,"Awa Parent",null,null,"MOTHER","NO_PORTAL",null,true,true,1,true,true,true,true,true,true,false,true,null);
  var sibling=registrations.register(new RegistrationRequest(student("Moussa","Nana"),List.of(existing)));
  assertThat(sibling.guardians().getFirst().guardianId()).isEqualTo(guardianId);
  assertThat(guardians.search("awa@example.test").getFirst().linkedChildren()).isEqualTo(2);
 }

 @Test void familyImportDryRunDoesNotMutateAndCommitIsRetrySafe(){
  var row=new FamilyImportRow("ROW-1","Lina","Toko","F",null,classId,List.of(new FamilyImportGuardian("Mme Toko","toko@example.test","6002","MOTHER","SEND_INVITE")));
  var preview=imports.dryRun(new FamilyImportRequest("test.csv",List.of(row)));
  assertThat(preview.validRows()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isZero();
  var committed=imports.commit(preview.jobId());assertThat(committed.createdRows()).isEqualTo(1);
  assertThatThrownBy(()->imports.commit(preview.jobId())).hasMessageContaining("état");
  String hash=jdbc.queryForObject("SELECT token_hash FROM guardian_account_token WHERE school_id=?",String.class,school);assertThat(hash).hasSize(64).doesNotContain("toko@example");
 }
 private StudentUpsert student(String first,String last){return new StudentUpsert(first,last,null,"F",LocalDate.of(2015,1,1),null,false,classId,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);}
}
