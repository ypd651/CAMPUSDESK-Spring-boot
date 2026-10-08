package com.technova.campusdesk.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JwtUtilTests {

    private final JwtProperties properties =
            new JwtProperties("test-secret-with-at-least-32-characters", "campusdesk", Duration.ofMinutes(7));
    private final JwtUtil jwtUtil = new JwtUtil(properties);

    @Test
    void createsTokenWithSubjectIssuerAndConfiguredExpiration() {
        String token = jwtUtil.create("ana@technova.local");

        var decoded = JWT.require(Algorithm.HMAC256(properties.secret()))
                .withIssuer(properties.issuer()).build().verify(token);
        long lifetime = decoded.getExpiresAtAsInstant().getEpochSecond() - decoded.getIssuedAtAsInstant().getEpochSecond();

        assertThat(lifetime).isEqualTo(420);
        assertThat(jwtUtil.getEmail(token)).isEqualTo("ana@technova.local");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void rejectsExpiredTamperedMalformedAndMissingTokens() {
        String expired = JWT.create().withSubject("ana@technova.local").withIssuer(properties.issuer())
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(properties.secret()));
        String otherKey = new JwtUtil(new JwtProperties("another-secret-with-at-least-32-chars!!", "campusdesk",
                Duration.ofMinutes(7))).create("ana@technova.local");

        assertThat(jwtUtil.isValid(expired)).isFalse();
        assertThat(jwtUtil.isValid(otherKey)).isFalse();
        assertThat(jwtUtil.isValid("not-a-token")).isFalse();
        assertThat(jwtUtil.isValid(null)).isFalse();
    }
}
