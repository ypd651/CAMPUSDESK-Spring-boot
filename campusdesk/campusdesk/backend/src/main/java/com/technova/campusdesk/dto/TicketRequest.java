package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

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
        TicketPriority priority,

        /** Optional deadline requested by the user. */
        @FutureOrPresent(message = "Due date cannot be in the past")
        LocalDate dueDate,

        /** Only used when editing: version of the ticket the client saw (409 if it changed meanwhile). */
        Long version) {

    public TicketRequest(String title, String description, TicketCategory category,
                         TicketPriority priority, LocalDate dueDate) {
        this(title, description, category, priority, dueDate, null);
    }
}
