package com.bbc.sms.messaging;

import com.bbc.sms.platform.security.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.messaging.dto.MessageDtos.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrespondenceScopeTest {
    final UUID school=UUID.randomUUID(), own=UUID.randomUUID(), foreign=UUID.randomUUID();
    final CorrespondenceRepository repo=mock(CorrespondenceRepository.class);
    final StudentRepository students=mock(StudentRepository.class);
    final TeacherScopeService scope=mock(TeacherScopeService.class);
    final CorrespondenceService service=new CorrespondenceService(repo,students,mock(AuthorizationPolicyService.class),scope);
    @BeforeEach void setup(){TenantContext.set(school);}
    @AfterEach void cleanup(){TenantContext.clear();}
    @Test void listFiltersStudentMessagesUsingTheSameRosterBoundary(){
        var a=notice(own);var b=notice(foreign);
        when(scope.allowedStudentIds()).thenReturn(Set.of(own));
        when(repo.findBySchoolIdOrderByCreatedAtDesc(school)).thenReturn(List.of(a,b));
        assertThat(service.list()).extracting(NoticeView::studentId).containsExactly(own);
    }
    @Test void foreignStudentCannotBeQueriedCreatedAcknowledgedOrDeleted(){
        doThrow(ApiException.forbidden("Outside assigned classes")).when(scope).assertStudent(foreign);
        var notice=notice(foreign);when(repo.findByIdAndSchoolId(notice.getId(),school)).thenReturn(Optional.of(notice));
        assertThatThrownBy(()->service.forStudent(foreign)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.create(new NoticeUpsert(foreign,"info","QA","QA",false))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.acknowledge(notice.getId(),new AckRequest("Forbidden"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.delete(notice.getId())).isInstanceOf(ApiException.class);
        verify(repo,never()).save(any());verify(repo,never()).delete(any());
    }
    Correspondence notice(UUID pupil){var n=new Correspondence();n.setId(UUID.randomUUID());n.setStudentId(pupil);n.setSchoolId(school);return n;}
}
