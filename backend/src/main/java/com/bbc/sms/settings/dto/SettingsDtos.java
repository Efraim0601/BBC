package com.bbc.sms.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class SettingsDtos {

    public record RoleView(String code, String labelFr, String labelEn, boolean builtin) {}

    /** Full permission matrix for the Settings editor (§13.1). */
    public record PermissionMatrix(
            List<String> modules,
            List<RoleView> roles,
            Map<String, Map<String, String>> matrix) {}   // role -> module -> none|read|write

    public record PermissionUpdate(
            @NotBlank String roleCode,
            @NotBlank String module,
            @NotBlank String level) {}

    public record UpdateRequest(@NotNull List<PermissionUpdate> updates) {}

    // ---- SMTP / mail configuration (§ admin) --------------------------------
    /** Current SMTP config for the editor. The password is never echoed back. */
    public record MailConfigView(
            boolean enabled,
            String host,
            int port,
            String username,
            boolean passwordSet,
            String fromAddress,
            String fromName,
            boolean useTls,
            boolean notifyOnUserCreate) {}

    /** Update payload. A blank/absent password keeps the stored one. */
    public record MailConfigUpdate(
            boolean enabled,
            String host,
            Integer port,
            String username,
            String password,
            String fromAddress,
            String fromName,
            Boolean useTls,
            Boolean notifyOnUserCreate) {}

    public record TestMailRequest(@NotBlank String to) {}
}
