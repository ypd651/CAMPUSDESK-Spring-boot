package com.technova.campusdesk.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** First administrator, read from environment variables (app.bootstrap.admin.*). */
@Validated
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(@NotNull @Valid Admin admin) {

    public record Admin(
            String fullName,
            @NotBlank @Email String email,
            @NotBlank String password) {
    }
}
