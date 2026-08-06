package com.bbc.sms.guardian;

import com.bbc.sms.student.StudentRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.bbc.sms.guardian.GuardianDtos.*;

@RestController
public class GuardianController {
    private final GuardianService guardians; private final GuardianAccountService accounts;
    private final StudentRegistrationService registration; private final FamilyImportService imports;
    public GuardianController(GuardianService guardians,GuardianAccountService accounts,StudentRegistrationService registration,FamilyImportService imports){this.guardians=guardians;this.accounts=accounts;this.registration=registration;this.imports=imports;}

    @GetMapping("/api/guardians/search") @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public List<GuardianSearchView> search(@RequestParam String q){return guardians.search(q);}
    @GetMapping("/api/students/{studentId}/guardians") @PreAuthorize("@perm.can('students','read') and @perm.staffOnly()")
    public List<GuardianRelationshipView> list(@PathVariable UUID studentId){return guardians.list(studentId);}
    @PostMapping("/api/students/{studentId}/guardians") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public GuardianRelationshipView add(@PathVariable UUID studentId,@Valid @RequestBody GuardianInput in){return guardians.add(studentId,in);}
    @PutMapping("/api/student-guardian-relationships/{id}") @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public GuardianRelationshipView update(@PathVariable UUID id,@Valid @RequestBody RelationshipUpsert in){return guardians.update(id,in);}
    @DeleteMapping("/api/student-guardian-relationships/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public void end(@PathVariable UUID id,@RequestParam String reason){guardians.end(id,reason);}
    @PostMapping("/api/guardians/{id}/merge") @PreAuthorize("@perm.can('settings','write') and @perm.staffOnly()")
    public GuardianSearchView merge(@PathVariable UUID id,@Valid @RequestBody MergeRequest in){return guardians.merge(id,in);}
    @PostMapping("/api/guardians/{id}/resend-invite") @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public InviteResult invite(@PathVariable UUID id){return accounts.issueInvite(id);}
    @PostMapping("/api/guardians/{id}/deactivate") @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public void deactivate(@PathVariable UUID id,@Valid @RequestBody LifecycleRequest in){accounts.deactivate(id,in.reason());}
    @PostMapping("/api/guardians/{id}/reactivate") @PreAuthorize("@perm.canAction('GUARDIAN_LINK') and @perm.staffOnly()")
    public void reactivate(@PathVariable UUID id,@Valid @RequestBody LifecycleRequest in){accounts.reactivate(id,in.reason());}

    @PostMapping("/api/student-registrations") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public StudentRegistrationService.RegistrationView register(@Valid @RequestBody StudentRegistrationService.RegistrationRequest in){return registration.register(in);}
    @PostMapping("/api/family-imports/dry-run") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public FamilyImportView dryRun(@Valid @RequestBody FamilyImportRequest in){return imports.dryRun(in);}
    @PostMapping("/api/family-imports/{id}/commit") @PreAuthorize("@perm.can('students','write') and @perm.staffOnly()")
    public FamilyImportView commit(@PathVariable UUID id){return imports.commit(id);}

    @PostMapping("/api/auth/parent/invitations/accept") public PublicMessage accept(@Valid @RequestBody AcceptInviteRequest in){return accounts.accept(in);}
    @PostMapping("/api/auth/parent/forgot-password") public PublicMessage forgot(@Valid @RequestBody ForgotParentPasswordRequest in){return accounts.forgot(in);}
    @PostMapping("/api/auth/parent/reset-password") public PublicMessage reset(@Valid @RequestBody ResetParentPasswordRequest in){return accounts.reset(in);}
}
