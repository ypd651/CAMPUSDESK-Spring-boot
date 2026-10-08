package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.Permission;

public record PermissionResponse(String name, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.name(), permission.getDescription());
    }
}
