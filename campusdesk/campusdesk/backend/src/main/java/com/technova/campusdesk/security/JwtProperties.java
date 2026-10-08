package com.technova.campusdesk.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bound from security.jwt.* (values come from environment variables, never from source code). */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET must have at least 32 characters") String secret,
        @NotBlank String issuer,
        @NotNull Duration expiration) {

    @AssertTrue(message = "security.jwt.expiration must be at least one second")
    public boolean isExpirationValid() {
        return expiration != null && expiration.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
