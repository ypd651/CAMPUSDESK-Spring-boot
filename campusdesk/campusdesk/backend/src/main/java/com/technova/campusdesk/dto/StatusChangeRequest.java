package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
        @NotNull(message = "Status is required")
        TicketStatus status,

        @Size(max = 255, message = "Note must have at most 255 characters")
        String note) {
}
