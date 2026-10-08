package com.technova.campusdesk.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Creates and verifies HMAC256-signed tokens. The subject is the user's email. */
@Component
public class JwtUtil {

    private final JwtProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.algorithm = Algorithm.HMAC256(properties.secret());
        this.verifier = JWT.require(algorithm).withIssuer(properties.issuer()).build();
    }

    public String create(String email) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(email)
                .withIssuer(properties.issuer())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(properties.expiration()))
                .sign(algorithm);
    }

    /** False for missing, malformed, tampered, wrong-issuer or expired tokens. */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException exception) {
            return false;
        }
    }

    public String getEmail(String token) {
        return verifier.verify(token).getSubject();
    }

    public long expirationSeconds() {
        return properties.expiration().toSeconds();
    }
}
