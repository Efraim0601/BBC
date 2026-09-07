package com.bbc.sms.staff;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.TeacherScopeService;
import com.bbc.sms.platform.storage.ObjectStorage;
import com.bbc.sms.platform.tenant.TenantContext;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffDocumentScopeTest {
    final EmployeeRepository employees=mock(EmployeeRepository.class);
    final StaffDocumentRepository documents=mock(StaffDocumentRepository.class);
    final ObjectStorage storage=mock(ObjectStorage.class);
    final TeacherScopeService scope=mock(TeacherScopeService.class);
    final StaffDocumentService service=new StaffDocumentService(documents,employees,storage,scope);
    @AfterEach void clear(){TenantContext.clear();}
    private Employee fixture(String level){
        TenantContext.set(UUID.randomUUID());var e=new Employee();e.setId(UUID.randomUUID());e.setLevel(level);
        when(employees.findByIdAndSchoolId(e.getId(),TenantContext.get())).thenReturn(Optional.of(e));
        when(scope.staffLevelScope()).thenReturn("secondary");return e;
    }
    @Test void sharedContactDoesNotExposePrivateDocuments(){
        var e=fixture(null);
        assertThatThrownBy(()->service.list(e.getId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.download(e.getId(),UUID.randomUUID())).isInstanceOf(ApiException.class);
        verifyNoInteractions(documents,storage);
    }
    @Test void ownSectionDocumentsStayVisible(){
        var e=fixture("secondary");assertThat(service.list(e.getId())).isEmpty();
        verify(documents).findBySchoolIdAndEmployeeIdOrderByCreatedAtDesc(TenantContext.get(),e.getId());
    }
}
