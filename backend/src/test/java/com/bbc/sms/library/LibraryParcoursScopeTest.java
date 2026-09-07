package com.bbc.sms.library;

import com.bbc.sms.platform.security.*;
import com.bbc.sms.platform.storage.ObjectStorage;
import com.bbc.sms.platform.tenant.*;
import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryParcoursScopeTest {
    final UUID school = UUID.randomUUID(), user = UUID.randomUUID();
    final SharedResourceRepository repo = mock(SharedResourceRepository.class);
    final ObjectStorage storage = mock(ObjectStorage.class);
    final TeacherScopeService teachers = mock(TeacherScopeService.class);
    final PermissionService permissions = mock(PermissionService.class);
    final ParcoursAccessService parcours = mock(ParcoursAccessService.class);
    final LibraryService service = new LibraryService(repo, storage, teachers, permissions, mock(JdbcTemplate.class), parcours);

    @BeforeEach void setup() {
        TenantContext.set(school);
        AppUserPrincipal principal = new AppUserPrincipal(user, school, "principal", "principal", "Principal", "P");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        when(permissions.can("library","write")).thenReturn(true);
    }
    @AfterEach void cleanup() { TenantContext.clear(); ParcoursContext.clear(); SecurityContextHolder.clearContext(); }

    @Test void explicitPrincipalWithoutEmployeeSeesOnlyTheirCycleAndSharedReadOnlyFiles() {
        SharedResource primary = resource("primary"), secondary = resource("secondary"), shared = resource(null);
        when(parcours.allowed(user)).thenReturn(List.of(new ParcoursContext.Scope("secondary","FR")));
        when(repo.findBySchoolIdOrderByCreatedAtDesc(school)).thenReturn(List.of(primary,secondary,shared));
        var rows = service.list();
        assertThat(rows).extracting(r->r.id()).containsExactly(secondary.getId(),shared.getId());
        assertThat(rows.get(0).canEdit()).isTrue();
        assertThat(rows.get(1).canEdit()).isFalse();
        when(repo.findByIdAndSchoolId(primary.getId(),school)).thenReturn(Optional.of(primary));
        assertThatThrownBy(()->service.download(primary.getId())).isInstanceOf(ApiException.class);
        verifyNoInteractions(storage);
    }
    @Test void noAssignedParcoursIsNotUnrestricted() {
        when(parcours.allowed(user)).thenReturn(List.of());
        when(repo.findBySchoolIdOrderByCreatedAtDesc(school)).thenReturn(List.of(resource(null),resource("primary")));
        assertThat(service.list()).isEmpty();
    }
    @Test void selectedParcoursCannotBeForgedForDownload() {
        ParcoursContext.set(new ParcoursContext.Scope("primary","FR"));
        SharedResource primary=resource("primary");
        when(repo.findByIdAndSchoolId(primary.getId(),school)).thenReturn(Optional.of(primary));
        assertThatThrownBy(()->service.download(primary.getId())).isInstanceOf(ApiException.class);
        verifyNoInteractions(storage);
    }
    SharedResource resource(String section) { var r=new SharedResource();r.setId(UUID.randomUUID());r.setSchoolId(school);r.setSection(section);r.setPublished(true);r.setAudience("staff");return r; }
}
