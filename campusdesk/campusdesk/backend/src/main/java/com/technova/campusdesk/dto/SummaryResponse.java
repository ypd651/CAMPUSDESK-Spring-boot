package com.technova.campusdesk.dto;

import java.util.Map;

/** Dashboard indicators, counted in PostgreSQL and limited to the tickets the user may see. */
public record SummaryResponse(
        long total,
        long open,
        long assigned,
        long inProgress,
        long resolved,
        long closed,
        Map<String, Long> byPriority) {
}
