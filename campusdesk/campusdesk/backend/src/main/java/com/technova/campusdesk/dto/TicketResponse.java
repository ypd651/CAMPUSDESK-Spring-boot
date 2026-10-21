package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.Ticket;
import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * "availableActions" tells the UI what the current user may do right now (EDIT, ASSIGN, START, RESOLVE,
 * CLOSE, REOPEN, CANCEL, PRIORITY, COMMENT). It is computed by the same policy that the backend enforces.
 */
public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        UserSummary requester,
        UserSummary technician,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        LocalDate dueDate,
        Long version,
        List<String> availableActions) {

    public static TicketResponse from(Ticket ticket, List<String> availableActions) {
        return new TicketResponse(ticket.getId(), ticket.getTitle(), ticket.getDescription(),
                ticket.getCategory(), ticket.getPriority(), ticket.getStatus(),
                UserSummary.from(ticket.getRequester()), UserSummary.from(ticket.getTechnician()),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getResolvedAt(), ticket.getDueDate(), ticket.getVersion(), availableActions);
    }
}
