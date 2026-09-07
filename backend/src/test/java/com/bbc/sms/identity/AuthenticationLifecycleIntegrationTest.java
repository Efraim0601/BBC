package com.bbc.sms.identity;

import com.bbc.sms.identity.dto.AuthDtos.LoginRequest;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties={"bbc.bootstrap.enabled=false"})
class AuthenticationLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authentication_test").withUsername("bbc").withPassword("bbc");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired MockMvc mvc;
    @Autowired SessionTokenService sessions;
    private UUID id;
    private String username;

    @BeforeEach void fixture() {
        UUID schoolId=UUID.randomUUID(); id=UUID.randomUUID(); username="auth-"+id;
        jdbc.update("INSERT INTO school(id,code,name) VALUES (?,?,?)",schoolId,"S"+id.toString().substring(0,12),"Authentication test");
        jdbc.update("INSERT INTO app_user(id,school_id,username,password_hash,display_name,initials,role_code,active,parcours_scope_mode) VALUES (?,?,?,?,?,'AT','accountant',true,'GLOBAL')",
                id,schoolId,username,encoder.encode("Password7!"),"Authentication test");
    }

    @Test void failedAttemptsAreCommittedDespiteTheUnauthorizedResponse() {
        for(int attempt=1;attempt<=5;attempt++) {
            assertThatThrownBy(() -> auth.login(new LoginRequest(username,"wrong",null))).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
            assertThat(jdbc.queryForObject("SELECT failed_attempts FROM app_user WHERE id=?",Integer.class,id)).isEqualTo(attempt);
        }
        assertThatThrownBy(() -> auth.login(new LoginRequest(username,"Password7!",null))).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.LOCKED));
        jdbc.update("UPDATE app_user SET locked_until=now()-interval '1 minute' WHERE id=?",id);
        assertThat(auth.login(new LoginRequest(username,"Password7!",null)).accessToken()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM app_user WHERE id=?",Integer.class,id)).isZero();
    }

    @Test void concurrentFailuresCannotLoseAttempts() throws Exception {
        try(var pool=Executors.newFixedThreadPool(5)) {
            var jobs=IntStream.range(0,5).<Callable<Integer>>mapToObj(n -> () -> {
                try {auth.login(new LoginRequest(username,"wrong",null));return 200;}
                catch(ApiException ex){return ex.getStatus().value();}
            }).toList();
            for(var result:pool.invokeAll(jobs)) assertThat(result.get()).isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM app_user WHERE id=?",Integer.class,id)).isEqualTo(5);
    }

    @Test void deactivationAndPasswordVersionChangesRevokeAccessAndRefresh() throws Exception {
        var tokens=auth.login(new LoginRequest(username,"Password7!",null));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+tokens.accessToken())).andExpect(status().isOk());
        jdbc.update("UPDATE app_user SET active=false WHERE id=?",id);
        assertRejected(tokens.accessToken(),tokens.refreshToken());
        jdbc.update("UPDATE app_user SET active=true,credentials_version=credentials_version+1 WHERE id=?",id);
        assertRejected(tokens.accessToken(),tokens.refreshToken());
        assertThat(auth.login(new LoginRequest(username,"Password7!",null)).accessToken()).isNotBlank();
    }

    @Test void archivedEmployeeCannotLoginEvenIfOldUserFlagWasLeftActive() {
        UUID school=jdbc.queryForObject("SELECT school_id FROM app_user WHERE id=?",UUID.class,id);
        UUID employee=UUID.randomUUID();
        jdbc.update("INSERT INTO employee(id,school_id,code,name,initials,type,monthly_salary,hourly_rate,active) VALUES (?,?,?,'Archived staff','AS','Permanent',0,0,false)",employee,school,"EMP-"+id.toString().substring(0,12));
        jdbc.update("UPDATE app_user SET employee_id=? WHERE id=?",employee,id);
        assertThatThrownBy(() -> auth.login(new LoginRequest(username,"Password7!",null))).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test void anonymousSessionEndpointIsUnauthorizedNotNotFound() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    private void assertRejected(String access,String refresh) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\""+refresh+"\"}")).andExpect(status().isUnauthorized());
    }
}
