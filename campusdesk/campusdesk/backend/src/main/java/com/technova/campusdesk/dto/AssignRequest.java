package com.technova.campusdesk.dto;

import jakarta.validation.constraints.NotNull;

public record AssignRequest(
        @NotNull(message = "Technician id is required")
        Long technicianId,

        /** Version of the ticket the client saw; if it changed in the meantime the request fails with 409. */
        Long version) {

    public AssignRequest(Long technicianId) {
        this(technicianId, null);
    }
}
