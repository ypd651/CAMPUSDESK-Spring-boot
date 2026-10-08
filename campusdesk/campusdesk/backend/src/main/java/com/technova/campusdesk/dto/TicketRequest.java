package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 150, message = "Title must have between 5 and 150 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 4000, message = "Description must have between 10 and 4000 characters")
        String description,

        @NotNull(message = "Category is required")
        TicketCategory category,

        @NotNull(message = "Priority is required")
        TicketPriority priority) {
}
