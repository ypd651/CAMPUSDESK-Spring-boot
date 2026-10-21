package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.AuthResponse;
import com.technova.campusdesk.dto.ChangePasswordRequest;
import com.technova.campusdesk.dto.LoginRequest;
import com.technova.campusdesk.dto.RegisterRequest;
import com.technova.campusdesk.dto.UserResponse;
import com.technova.campusdesk.security.UserPrincipal;
import com.technova.campusdesk.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login and current session")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "Register a new user (administrators only)",
            description = "There is no public sign-up. An administrator creates the account, which always gets the USER role.")
    public UserResponse register(@Valid @RequestBody RegisterRequest request,
                                 @AuthenticationPrincipal UserPrincipal actor) {
        return authService.register(request, actor);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a JWT to send as 'Authorization: Bearer <token>'.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password", description = "Requires the current password and a new one that meets the policy.")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               @AuthenticationPrincipal UserPrincipal actor) {
        authService.changePassword(actor, request);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user, role and permissions")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal);
    }
}
