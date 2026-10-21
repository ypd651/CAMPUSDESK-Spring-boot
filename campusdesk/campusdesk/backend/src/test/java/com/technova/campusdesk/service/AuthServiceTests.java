package com.technova.campusdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.technova.campusdesk.dto.ChangePasswordRequest;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BadRequestException;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.JwtUtil;
import com.technova.campusdesk.security.UserPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests of the change-password rules (real BCrypt, mocked repository). */
class AuthServiceTests {

    private static final String CURRENT = "Current-Pass-1!";

    private PasswordEncoder encoder;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        UserRepository users = mock(UserRepository.class);
        authService = new AuthService(users, mock(UserService.class), encoder, mock(JwtUtil.class));

        Role role = new Role();
        role.setName(Role.USER);
        user = new User();
        user.setId(7L);
        user.setFullName("Laura Gomez");
        user.setEmail("laura@technova.local");
        user.setPasswordHash(encoder.encode(CURRENT));
        user.setRole(role);
        user.setEnabled(true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void changesThePasswordAndStoresOnlyTheHash() {
        authService.changePassword(UserPrincipal.from(user), new ChangePasswordRequest(CURRENT, "Brand-New-Pass-2@"));

        assertThat(encoder.matches("Brand-New-Pass-2@", user.getPasswordHash())).isTrue();
        assertThat(user.getPasswordHash()).doesNotContain("Brand-New-Pass-2@");
    }

    @Test
    void rejectsAWrongCurrentPassword() {
        String before = user.getPasswordHash();

        assertThatThrownBy(() -> authService.changePassword(UserPrincipal.from(user),
                new ChangePasswordRequest("Wrong-Pass-9#", "Brand-New-Pass-2@")))
                .isInstanceOf(BadRequestException.class);
        assertThat(user.getPasswordHash()).isEqualTo(before);
    }

    @Test
    void rejectsReusingTheCurrentPassword() {
        assertThatThrownBy(() -> authService.changePassword(UserPrincipal.from(user),
                new ChangePasswordRequest(CURRENT, CURRENT)))
                .isInstanceOf(BadRequestException.class);
    }
}
