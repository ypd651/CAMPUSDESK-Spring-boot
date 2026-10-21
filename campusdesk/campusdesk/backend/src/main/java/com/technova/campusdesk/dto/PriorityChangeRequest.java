package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.TicketPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PriorityChangeRequest(
        @NotNull(message = "Priority is required")
        TicketPriority priority,

        @Size(max = 255, message = "Note must have at most 255 characters")
        String note,

        /** Version of the ticket the client saw; if it changed in the meantime the request fails with 409. */
        Long version) {

    public PriorityChangeRequest(TicketPriority priority, String note) {
        this(priority, note, null);
    }
}
