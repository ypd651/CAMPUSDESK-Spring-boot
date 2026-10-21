package com.technova.campusdesk.dto;

import java.util.List;
import java.util.Map;

/** Advanced statistics (administrators): resolution times, tickets per category and per technician. */
public record AdvancedReportResponse(
        Double averageResolutionHours,
        Map<String, Long> byCategory,
        List<TechnicianStats> technicians) {

    /** "assigned" counts every ticket given to the technician; "attended" the ones they marked as resolved. */
    public record TechnicianStats(
            Long technicianId,
            String fullName,
            long assigned,
            long attended,
            Double averageResolutionHours) {
    }
}
