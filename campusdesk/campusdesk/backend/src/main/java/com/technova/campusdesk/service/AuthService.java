package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.AuthResponse;
import com.technova.campusdesk.dto.LoginRequest;
import com.technova.campusdesk.dto.RegisterRequest;
import com.technova.campusdesk.dto.UserResponse;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BusinessRuleException;
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.exception.UnauthorizedException;
import com.technova.campusdesk.repository.RoleRepository;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.JwtUtil;
import com.technova.campusdesk.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository users, RoleRepository roles,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /** Public registration always creates a USER account; privileged roles are provisioned by an administrator. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Email is already registered");
        }
        Role role = roles.findById(Role.USER)
                .orElseThrow(() -> new IllegalStateException("Default role USER is missing"));

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setEnabled(true);
        return UserResponse.from(users.save(user));
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
