package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.StatusHistory;
import com.technova.campusdesk.entity.TicketStatus;
import java.time.Instant;

public record HistoryResponse(
        Long id,
        TicketStatus previousStatus,
        TicketStatus newStatus,
        UserSummary changedBy,
        Instant changedAt,
        String note) {

    public static HistoryResponse from(StatusHistory entry) {
        return new HistoryResponse(entry.getId(), entry.getPreviousStatus(), entry.getNewStatus(),
                UserSummary.from(entry.getChangedBy()), entry.getChangedAt(), entry.getNote());
    }
}
