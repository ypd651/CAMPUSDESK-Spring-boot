package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String role,
        List<String> permissions,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        List<String> permissions = user.getRole().getPermissions().stream()
                .sorted(Comparator.comparingInt(Permission::ordinal))
                .map(Permission::name)
                .toList();
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole().getName(), permissions, user.isEnabled(), user.getCreatedAt());
    }
}
