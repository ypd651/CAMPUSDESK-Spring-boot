package com.technova.campusdesk.dto;

import jakarta.validation.constraints.NotNull;

public record AssignRequest(
        @NotNull(message = "Technician id is required")
        Long technicianId) {
}
