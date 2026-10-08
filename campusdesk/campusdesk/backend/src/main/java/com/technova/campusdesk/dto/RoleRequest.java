package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Used to create and to update custom roles (the name is ignored on update). */
public record RoleRequest(
        @NotBlank(message = "Role name is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$",
                message = "Role name must be upper case letters, digits or underscores (2-50 characters)")
        String name,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must have at most 255 characters")
        String description,

        @NotEmpty(message = "Select at least one permission")
        Set<Permission> permissions) {
}
