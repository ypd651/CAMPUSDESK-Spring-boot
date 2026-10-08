package com.technova.campusdesk.dto;

import jakarta.validation.constraints.Size;

/** Partial update: null fields are left unchanged. */
public record UpdateUserRequest(
        @Size(min = 1, max = 120, message = "Full name must have between 1 and 120 characters")
        String fullName,
        String role,
        Boolean enabled) {
}
