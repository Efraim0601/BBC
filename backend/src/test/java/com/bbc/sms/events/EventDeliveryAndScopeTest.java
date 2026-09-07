package com.bbc.sms.events;

import com.bbc.sms.platform.security.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.realtime.RealtimeService;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.student.StudentRepository;
import com.bbc.sms.events.dto.EventDtos.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventDeliveryAndScopeTest {
    final UUID school=UUID.randomUUID();
    final EventRepository repo=mock(EventRepository.class);
    final TeacherScopeService scope=mock(TeacherScopeService.class);
    final RealtimeService realtime=mock(RealtimeService.class);
    final EventService service=new EventService(repo,mock(StudentRepository.class),realtime,mock(AuthorizationPolicyService.class),scope);
    @BeforeEach void setup(){TenantContext.set(school);}
    @AfterEach void cleanup(){TenantContext.clear();}
    @Test void noProviderNeverMarksEventNotified(){
        when(scope.allowedClassNames()).thenReturn(null);
        SchoolEvent e=event("CE1 A");when(repo.findByIdAndSchoolId(e.getId(),school)).thenReturn(Optional.of(e));
        assertThatThrownBy(()->service.notify(e.getId())).isInstanceOf(ApiException.class).hasMessageContaining("Aucun message");
        assertThat(e.isNotified()).isFalse();verify(repo,never()).save(any());verifyNoInteractions(realtime);
    }
    @Test void scopedPrincipalDoesNotSeeOrModifyForeignClassEvent(){
        SchoolEvent primary=event("CE1 A"), secondary=event("6ème A");
        when(scope.allowedClassNames()).thenReturn(Set.of("6ème A"));
        when(repo.findBySchoolIdOrderByEventDateDesc(school)).thenReturn(List.of(primary,secondary));
        assertThat(service.list()).extracting(EventView::id).containsExactly(secondary.getId());
        when(repo.findByIdAndSchoolId(primary.getId(),school)).thenReturn(Optional.of(primary));
        assertThatThrownBy(()->service.delete(primary.getId())).isInstanceOf(ApiException.class);
        verify(repo,never()).delete(any());
    }
    SchoolEvent event(String cls){var e=new SchoolEvent();e.setId(UUID.randomUUID());e.setSchoolId(school);e.setAudience("classes");e.setTargetClasses(List.of(cls));return e;}
}
