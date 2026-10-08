package com.technova.campusdesk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-registration. There is deliberately no role field: new accounts are always USER. */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must have at most 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 150, message = "Email must have at most 150 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE)
        String password) {
}
