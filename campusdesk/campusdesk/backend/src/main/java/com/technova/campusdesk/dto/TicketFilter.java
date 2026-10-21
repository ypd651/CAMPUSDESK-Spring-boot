package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import java.time.LocalDate;

/** Optional ticket filters; every non-null value is combined with AND on top of the user's visibility scope. */
public record TicketFilter(
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        Long technicianId,
        Boolean unassigned,
        LocalDate createdFrom,
        LocalDate createdTo,
        String text) {

    public static TicketFilter of(TicketStatus status, TicketPriority priority, TicketCategory category) {
        return new TicketFilter(status, priority, category, null, null, null, null, null);
    }
}
