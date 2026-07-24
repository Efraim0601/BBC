package com.bbc.sms.identity.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthDtos {

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password,
            String schoolCode) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(
            @NotBlank String username,
            String schoolCode) {}

    /** Always-safe response (does not reveal whether the username exists). */
    public record ForgotPasswordResponse(boolean ok, String message) {}

    /** A parcours the user may access (level × subsystem). */
    public record Parcours(String level, String subsystem) {}

    public record UserView(
            UUID id,
            String username,
            String displayName,
            String initials,
            String role,
            UUID schoolId,
            String schoolCode,
            String schoolName,
            String locale,
            Map<String, String> permissions,   // module -> none|read|write
            List<String> modules,               // modules the role may open
            List<Parcours> allowedParcours) {}  // empty = all parcours (admin)

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInMs,
            UserView user) {}
}
