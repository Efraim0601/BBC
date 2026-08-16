package com.bbc.sms.settings;

import com.bbc.sms.settings.dto.AccessControlDtos.*;
import com.bbc.sms.platform.security.PolicyDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Staged Access Control workspace API. */
@RestController
@RequestMapping({"/api/access", "/api/access-control"})
public class AccessControlController {
    private final AccessControlService access;

    public AccessControlController(AccessControlService access) {
        this.access = access;
    }

    @GetMapping("/catalog")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public List<ActionGroupView> catalog() {
        return access.actionGroups();
    }

    @GetMapping("/roles/{roleCode}")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public RoleWorkspace role(@PathVariable String roleCode) {
        return access.roleWorkspace(roleCode);
    }

    @GetMapping("/roles")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public List<com.bbc.sms.settings.dto.SettingsDtos.RoleView> roles() {
        return access.roles();
    }

    @PostMapping("/roles/{roleCode}/preview")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public PolicyPreview previewRole(@PathVariable String roleCode,
                                     @Valid @RequestBody RoleMutation mutation) {
        return access.previewRole(roleCode, mutation);
    }

    @PutMapping("/roles/{roleCode}")
    @PreAuthorize("@policy.canAction('PERMISSION_MANAGE')")
    public RoleWorkspace updateRole(@PathVariable String roleCode,
                                    @Valid @RequestBody RoleMutation mutation) {
        return access.updateRole(roleCode, mutation);
    }

    @GetMapping("/templates")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public List<TemplateView> templates() {
        return access.templates();
    }

    @PostMapping("/roles/{roleCode}/template-preview/{templateCode}")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public PolicyPreview previewTemplate(@PathVariable String roleCode,
                                         @PathVariable String templateCode) {
        return access.previewTemplate(roleCode, templateCode);
    }

    @PostMapping("/roles/{roleCode}/apply-template/{templateCode}")
    @PreAuthorize("@policy.canAction('PERMISSION_MANAGE')")
    public RoleWorkspace applyTemplate(@PathVariable String roleCode,
                                       @PathVariable String templateCode,
                                       @Valid @RequestBody TemplateApplyRequest request) {
        return access.applyTemplate(roleCode, templateCode, request.expectedPolicyVersion(), request.reason(),
                request.confirmHighRisk());
    }

    @GetMapping("/users")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public List<UserSelection> users(@RequestParam(required = false) String search) {
        return access.users(search);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public UserWorkspace user(@PathVariable UUID userId) {
        return access.userWorkspace(userId);
    }

    @PostMapping("/users/{userId}/preview")
    @PreAuthorize("@policy.canAction('PERMISSION_VIEW')")
    public PolicyPreview previewUser(@PathVariable UUID userId,
                                     @Valid @RequestBody UserMutation mutation) {
        return access.previewUser(userId, mutation);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("@policy.canAction('PERMISSION_MANAGE')")
    public UserWorkspace updateUser(@PathVariable UUID userId,
                                    @Valid @RequestBody UserMutation mutation) {
        return access.updateUser(userId, mutation);
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("@policy.canAction('ROLE_MANAGE')")
    public UserWorkspace updateUserRoles(@PathVariable UUID userId,
                                         @Valid @RequestBody RoleAssignmentMutation mutation) {
        return access.updateUserRoles(userId, mutation);
    }

    @GetMapping("/me/capabilities")
    @PreAuthorize("isAuthenticated()")
    public CapabilityView capabilities() {
        return access.capabilities();
    }

    @PostMapping("/me/decision")
    @PreAuthorize("isAuthenticated()")
    public PolicyDecision decision(@Valid @RequestBody ContextDecisionRequest request) {
        return access.contextDecision(request);
    }

    @GetMapping("/audit")
    @PreAuthorize("@policy.canAction('ACADEMIC_ACCESS_AUDIT_VIEW') or @policy.canAction('AUDIT_VIEW')")
    public List<AuditView> audit(@RequestParam(defaultValue = "100") int limit) {
        return access.audit(limit);
    }

    public record TemplateApplyRequest(@NotNull Long expectedPolicyVersion,
                                       @NotBlank String reason,
                                       boolean confirmHighRisk) {}
}
