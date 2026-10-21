package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.AuthResponse;
import com.technova.campusdesk.dto.ChangePasswordRequest;
import com.technova.campusdesk.dto.CreateUserRequest;
import com.technova.campusdesk.dto.LoginRequest;
import com.technova.campusdesk.dto.RegisterRequest;
import com.technova.campusdesk.dto.UserResponse;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BadRequestException;
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.exception.UnauthorizedException;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.JwtUtil;
import com.technova.campusdesk.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository users, UserService userService,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.users = users;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * There is no public self-registration: accounts are created by an administrator (the controller requires
     * USERS_MANAGE). This endpoint always creates a USER account; other roles use POST /api/users.
     */
    @Transactional
    public UserResponse register(RegisterRequest request, UserPrincipal actor) {
        return userService.create(new CreateUserRequest(request.fullName(), request.email(),
                request.password(), Role.USER), actor);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalize(request.email()))
                .filter(User::isEnabled)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        String token = jwtUtil.create(user.getEmail());
        return new AuthResponse(token, "Bearer", jwtUtil.expirationSeconds(), UserResponse.from(user));
    }

    /**
     * Changes the password of the current user. A wrong current password is a 400 (not 401), so the
     * frontend does not mistake it for an expired session. Existing tokens stay valid until they expire.
     */
    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = users.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UserPrincipal principal) {
        User user = users.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserResponse.from(user);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
