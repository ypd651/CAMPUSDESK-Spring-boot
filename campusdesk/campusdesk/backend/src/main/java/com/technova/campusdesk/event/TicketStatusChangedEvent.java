package com.technova.campusdesk.event;

import com.technova.campusdesk.entity.TicketStatus;

/**
 * Published by TicketService inside the transaction that changes a ticket's status. It carries plain values
 * (no entities) so listeners can run after the commit, even on another thread, without touching the database.
 */
public record TicketStatusChangedEvent(
        Long ticketId,
        String title,
        TicketStatus previousStatus,
        TicketStatus newStatus,
        Long changedById,
        String changedByName,
        Long requesterId,
        String requesterName,
        String requesterEmail,
        Long technicianId,
        String technicianName,
        String technicianEmail,
        boolean automatic) {
}
