package com.technova.campusdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
        String newPassword) {
}
