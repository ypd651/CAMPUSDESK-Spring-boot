package com.technova.campusdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * JSON answers for security failures raised before a controller runs:
 * 401 when the token is missing, invalid or expired; 403 when the role lacks access.
 */
@Component
public class RestSecurityHandlers {

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required or the token is invalid or expired");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                write(request, response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + status.getReasonPhrase() + "\","
                + "\"message\":\"" + message + "\","
                + "\"path\":\"" + escape(request.getRequestURI()) + "\"}";
        response.getWriter().write(body);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
