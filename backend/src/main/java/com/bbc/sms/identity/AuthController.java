package com.bbc.sms.identity;

import com.bbc.sms.identity.dto.AuthDtos.*;
import com.bbc.sms.platform.common.ApiException;
import com.bbc.sms.platform.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final AppUserRepository users;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService auth, AppUserRepository users,
                          PasswordResetService passwordReset) {
        this.auth = auth;
        this.users = users;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return passwordReset.requestReset(req);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) throw ApiException.notFound("Session");
        AppUser user = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("Utilisateur"));
        return auth.buildUserView(user);
    }
}
