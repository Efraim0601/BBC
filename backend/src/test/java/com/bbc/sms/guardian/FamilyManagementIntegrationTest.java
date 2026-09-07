package com.bbc.sms.guardian;

import com.bbc.sms.foundation.session.AcademicSessionService;
import com.bbc.sms.foundation.session.SessionDtos.SessionUpsert;
import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.common.ApiException;
import org.springframework.http.HttpStatus;
import com.bbc.sms.student.StudentRegistrationService;
import com.bbc.sms.student.StudentRegistrationService.RegistrationRequest;
import com.bbc.sms.student.StudentService;
import com.bbc.sms.student.dto.StudentDtos.StudentUpsert;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
 @Autowired JdbcTemplate jdbc;@Autowired AcademicSessionService sessions;@Autowired StudentRegistrationService registrations;@Autowired StudentService students;@Autowired GuardianService guardians;@Autowired FamilyImportService imports;
 UUID school,classId,actorUserId;
 @BeforeEach void setup(){
  school=UUID.randomUUID();classId=UUID.randomUUID();actorUserId=UUID.randomUUID();
  jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)",school,"F"+school.toString().substring(0,6),"Family school");
  jdbc.update("INSERT INTO role(code,label_fr,label_en,builtin) VALUES ('parent','Parent','Parent',true),('principal','Principal','Principal',true) ON CONFLICT DO NOTHING");
  jdbc.update("INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,active) VALUES (?,?,'family-test','test','Family test','FT','principal',true)",actorUserId,school);
  jdbc.update("INSERT INTO app_user_role(school_id,user_id,role_code,is_primary,reason) VALUES (?,?,'principal',true,'Family integration fixture') ON CONFLICT DO NOTHING",school,actorUserId);
  for(String action:new String[]{"STUDENT_PROFILE_CREATE","STUDENT_PROFILE_EDIT","STUDENT_IMPORT","GUARDIAN_DIRECTORY_SEARCH","GUARDIAN_DIRECTORY_MANAGE","GUARDIAN_LINK_MANAGE","GUARDIAN_ACCOUNT_MANAGE"})
   jdbc.update("INSERT INTO permission_role_action(school_id,role_code,action_code,effect,scope_mode,is_permanent,reason) VALUES (?,? ,?,'ALLOW','SCHOOL_ALL',true,'Family integration fixture') ON CONFLICT DO NOTHING",school,"principal",action);
  String section="s"+school.toString().substring(0,8);jdbc.update("INSERT INTO section(id,school_id,label,subsystem,level) VALUES (?,?,?,?,?)",section,school,"Primaire","FR","primary");jdbc.update("INSERT INTO school_class(id,school_id,section_id,name,subsystem,level) VALUES (?,?,?,?,?,?)",classId,school,section,"CM1-"+school.toString().substring(0,4),"FR","primary");TenantContext.set(school);
  AppUserPrincipal principal=new AppUserPrincipal(actorUserId,school,"family-test","principal","Family test","FT");
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  sessions.create(new SessionUpsert("2026-27","2026-2027",LocalDate.of(2026,8,1),LocalDate.of(2027,7,31),"OPEN",true,null,null,null,null,null));
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContext.clear();ParcoursContext.clear();}

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

 @Test void registrationAllowsNoEmailAndPortalAccessCanBeAddedLater(){
  GuardianInput noEmail=new GuardianInput(null,"Parent sans email",null,null,"MOTHER","NO_PORTAL",null,true,true,1,true,true,true,true,true,true,false,false,null);
  var registration=registrations.register(new RegistrationRequest(student("Lina","Toko"),List.of(noEmail)));
  UUID guardianId=registration.guardians().getFirst().guardianId();
  assertThat(registration.guardians().getFirst().email()).isNull();
  assertThat(registration.guardians().getFirst().portalAccess()).isFalse();
  assertThat(registration.guardians().getFirst().accountStatus()).isEqualTo("NO_PORTAL");
  assertThat(jdbc.queryForObject("SELECT app_user_id FROM guardian WHERE id=?",UUID.class,guardianId)).isNull();

  var updated=guardians.updatePortalAccess(registration.student().id(),guardianId,
      new GuardianPortalAccessInput("later@example.test","SEND_INVITE",null));
  assertThat(updated.email()).isEqualTo("later@example.test");
  assertThat(updated.portalAccess()).isTrue();
  assertThat(updated.accountStatus()).isEqualTo("INVITED");
  assertThat(updated.invitationStatus()).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE school_id=? AND normalized_email=?",Integer.class,school,"later@example.test")).isEqualTo(1);
 }

 @Test void registrationAllowsMissingFirstNameAndItCanBeAddedLater(){
  GuardianInput noPortal=new GuardianInput(null,"Single-name parent",null,null,"GUARDIAN","NO_PORTAL",null,true,true,1,true,true,true,true,true,true,false,false,null);
  var registration=registrations.register(new RegistrationRequest(student("","SingleName"),List.of(noPortal)));
  assertThat(registration.student().firstName()).isEmpty();
  assertThat(registration.student().name()).isEqualTo("SINGLENAME");
  assertThat(jdbc.queryForObject("SELECT first_name FROM student WHERE id=?",String.class,registration.student().id())).isEmpty();

  var updated=students.update(registration.student().id(),student("AddedLater","SingleName"));
  assertThat(updated.firstName()).isEqualTo("AddedLater");
  assertThat(updated.name()).isEqualTo("SINGLENAME AddedLater");
 }

 @Test void familyImportDryRunDoesNotMutateAndCommitIsRetrySafe(){
  var row=new FamilyImportRow("ROW-1","Lina","Toko","NIU-1","F",LocalDate.of(2015,2,3),"Douala",true,classId,List.of(new FamilyImportGuardian("M. Toko","father@example.test","6001","FATHER","SEND_INVITE"),new FamilyImportGuardian("Mme Toko","toko@example.test","6002","MOTHER","SEND_INVITE")));
  var preview=imports.dryRun(new FamilyImportRequest("test.csv",List.of(row)));
  assertThat(preview.validRows()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isZero();
  var committed=imports.commit(preview.jobId());assertThat(committed.createdRows()).isEqualTo(1);
  UUID studentId=jdbc.queryForObject("SELECT id FROM student WHERE school_id=?",UUID.class,school);
  assertThat(jdbc.queryForObject("SELECT niu FROM student WHERE id=?",String.class,studentId)).isEqualTo("NIU-1");
  assertThat(jdbc.queryForObject("SELECT birthplace FROM student WHERE id=?",String.class,studentId)).isEqualTo("Douala");
  assertThat(jdbc.queryForObject("SELECT repeats FROM student WHERE id=?",Boolean.class,studentId)).isTrue();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM student_guardian WHERE student_id=?",Integer.class,studentId)).isEqualTo(2);
  assertThatThrownBy(()->imports.commit(preview.jobId())).hasMessageContaining("état");
  List<String> hashes=jdbc.query("SELECT token_hash FROM guardian_account_token WHERE school_id=?",(rs,i)->rs.getString(1),school);assertThat(hashes).hasSize(2).allMatch(hash->hash.length()==64&&!hash.contains("toko@example"));
 }
 @Test void familyPreviewRejectsFutureBirthDateButKeepsFirstNameOptional(){
  var valid=importRow("VALID","SingleName",LocalDate.of(2015,1,1));
  var future=importRow("FUTURE","FutureChild",LocalDate.now().plusDays(1));
  var preview=imports.dryRun(new FamilyImportRequest("future.csv",List.of(valid,future)));
  assertThat(preview.validRows()).isEqualTo(1);
  assertThat(preview.rows().get(1).outcome()).isEqualTo("ERROR");
  var committed=imports.commit(preview.jobId());
  assertThat(committed.createdRows()).isEqualTo(1);
  assertThat(committed.failedRows()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=? AND dob>current_date",Integer.class,school)).isZero();
 }

 @Test void oneChangedRowDoesNotRollbackOtherFamiliesAndRetryKeepsTotals(){
  var preview=imports.dryRun(new FamilyImportRequest("partial.csv",List.of(
    importRow("DUP","AlreadyCreated",LocalDate.of(2015,1,1)),
    importRow("OK","StillValid",LocalDate.of(2015,1,1)))));
  students.create(student("","AlreadyCreated"));
  var committed=imports.commit(preview.jobId());
  assertThat(committed.status()).isEqualTo("COMPLETED_ERRORS");
  assertThat(committed.createdRows()).isEqualTo(1);
  assertThat(committed.linkedGuardians()).isEqualTo(1);
  assertThat(committed.failedRows()).isEqualTo(1);
  var retry=imports.commit(preview.jobId());
  assertThat(retry.createdRows()).isEqualTo(1);
  assertThat(retry.linkedGuardians()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isEqualTo(2);
 }

 @Test void familyImportCannotBeReadOrCommittedOutsideItsParcours(){
  var preview=imports.dryRun(new FamilyImportRequest("primary.csv",List.of(importRow("ONE","PrimaryChild",LocalDate.of(2015,1,1)))));
  ParcoursContext.set(new ParcoursContext.Scope("secondary","FR"));
  assertThatThrownBy(()->imports.view(preview.jobId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus()).isIn(HttpStatus.NOT_FOUND,HttpStatus.FORBIDDEN));
  assertThatThrownBy(()->imports.commit(preview.jobId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus()).isIn(HttpStatus.NOT_FOUND,HttpStatus.FORBIDDEN));
  assertThatThrownBy(()->imports.dryRun(new FamilyImportRequest("foreign.csv",List.of(importRow("TWO","ForeignChild",LocalDate.of(2015,1,1)))))).isInstanceOf(ApiException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isZero();
  ParcoursContext.clear();imports.commit(preview.jobId());
  ParcoursContext.set(new ParcoursContext.Scope("secondary","FR"));
  assertThatThrownBy(()->imports.commit(preview.jobId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus()).isIn(HttpStatus.NOT_FOUND,HttpStatus.FORBIDDEN));
 }

 @Test void unknownAndForeignFamilyImportJobsReturnNotFound(){
  assertThatThrownBy(()->imports.view(UUID.randomUUID())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
  var preview=imports.dryRun(new FamilyImportRequest("own.csv",List.of(importRow("ONE","OwnChild",LocalDate.of(2015,1,1)))));
  UUID other=UUID.randomUUID();jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)",other,"OTHER"+other.toString().substring(0,6),"Other school");
  jdbc.update("UPDATE family_import_job SET school_id=? WHERE id=?",other,preview.jobId());
  assertThatThrownBy(()->imports.commit(preview.jobId())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
 }

 @Test void internalRegistrationCannotBypassBirthDateValidation(){
  var future=new StudentUpsert("","FutureInternal",null,"F",LocalDate.now().plusDays(1),null,false,classId,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,false);
  assertThatThrownBy(()->students.create(future)).isInstanceOf(ApiException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isZero();
 }

 @Test void concurrentFamilyCommitCreatesEachPupilOnlyOnce() throws Exception {
  var preview=imports.dryRun(new FamilyImportRequest("concurrent.csv",List.of(importRow("ONE","ConcurrentChild",LocalDate.of(2015,1,1)))));
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  var gate=new java.util.concurrent.CountDownLatch(1);
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  java.util.concurrent.Callable<Integer> task=()->{
   TenantContext.set(school);SecurityContextHolder.getContext().setAuthentication(authentication);
   try { gate.await();imports.commit(preview.jobId());return 200; }
   catch(ApiException e){return e.getStatus().value();}
   finally {TenantContext.clear();SecurityContextHolder.clearContext();}
  };
  try {
   var first=executor.submit(task);var second=executor.submit(task);gate.countDown();
   assertThat(List.of(first.get(30,java.util.concurrent.TimeUnit.SECONDS),second.get(30,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id=?",Integer.class,school)).isEqualTo(1);
   assertThat(imports.view(preview.jobId()).createdRows()).isEqualTo(1);
  } finally {executor.shutdownNow();}
 }

 private FamilyImportRow importRow(String key,String last,LocalDate dob){return new FamilyImportRow(key,"",last,null,"F",dob,null,false,classId,List.of(new FamilyImportGuardian("Parent "+last,null,null,"GUARDIAN","NO_PORTAL")));}
 private StudentUpsert student(String first,String last){return new StudentUpsert(first,last,null,"F",LocalDate.of(2015,1,1),null,false,classId,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,false);}
}
