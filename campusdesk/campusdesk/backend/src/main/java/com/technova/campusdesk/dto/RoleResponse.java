package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Role;
import java.util.Comparator;
import java.util.List;

public record RoleResponse(
        String name,
        String description,
        boolean systemRole,
        List<String> permissions,
        long userCount) {

    public static RoleResponse from(Role role, long userCount) {
        List<String> permissions = role.getPermissions().stream()
                .sorted(Comparator.comparingInt(Permission::ordinal))
                .map(Permission::name)
                .toList();
        return new RoleResponse(role.getName(), role.getDescription(), role.isSystemRole(), permissions, userCount);
    }
}
