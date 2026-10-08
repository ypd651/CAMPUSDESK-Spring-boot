package com.technova.campusdesk.dto;

import java.time.Instant;
import java.util.Map;

/** Uniform error body. It never contains stack traces, tokens or passwords. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
