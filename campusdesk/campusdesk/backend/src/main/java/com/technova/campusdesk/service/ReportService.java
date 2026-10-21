package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.AdvancedReportResponse;
import com.technova.campusdesk.dto.AdvancedReportResponse.TechnicianStats;
import com.technova.campusdesk.dto.SummaryResponse;
import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import com.technova.campusdesk.repository.TicketRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard indicators: COUNT queries executed by PostgreSQL, scoped to what the user may see. */
@Service
public class ReportService {

    private final TicketRepository tickets;
    private final TicketAccessPolicy policy;

    public ReportService(TicketRepository tickets, TicketAccessPolicy policy) {
        this.tickets = tickets;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(UserPrincipal actor) {
        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (TicketPriority priority : TicketPriority.values()) {
            byPriority.put(priority.name(), tickets.count(policy.visibleTo(actor, null, priority, null)));
        }
        return new SummaryResponse(
                tickets.count(policy.visibleTo(actor, null, null, null)),
                countByStatus(actor, TicketStatus.OPEN),
                countByStatus(actor, TicketStatus.ASSIGNED),
                countByStatus(actor, TicketStatus.IN_PROGRESS),
                countByStatus(actor, TicketStatus.RESOLVED),
                countByStatus(actor, TicketStatus.CLOSED),
                countByStatus(actor, TicketStatus.CANCELLED),
                byPriority);
    }

    /** Global statistics; the controller restricts this endpoint to users who can read every ticket. */
    @Transactional(readOnly = true)
    public AdvancedReportResponse advanced() {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (TicketCategory category : TicketCategory.values()) {
            byCategory.put(category.name(), 0L);
        }
        for (Object[] row : tickets.countByCategory()) {
            byCategory.put(((TicketCategory) row[0]).name(), (Long) row[1]);
        }

        Map<Long, long[]> perTechnician = new HashMap<>();   // [attended, totalMinutes]
        long totalMinutes = 0;
        long resolvedCount = 0;
        for (Object[] row : tickets.findResolutionTimes()) {
            long minutes = Duration.between((Instant) row[1], (Instant) row[2]).toMinutes();
            long[] acc = perTechnician.computeIfAbsent((Long) row[0], key -> new long[2]);
            acc[0]++;
            acc[1] += minutes;
            totalMinutes += minutes;
            resolvedCount++;
        }

        List<TechnicianStats> technicians = new ArrayList<>();
        for (Object[] row : tickets.countAssignedByTechnician()) {
            Long technicianId = (Long) row[0];
            long[] acc = perTechnician.getOrDefault(technicianId, new long[2]);
            technicians.add(new TechnicianStats(technicianId, (String) row[1], (Long) row[2],
                    acc[0], average(acc[1], acc[0])));
        }
        return new AdvancedReportResponse(average(totalMinutes, resolvedCount), byCategory, technicians);
    }

    private long countByStatus(UserPrincipal actor, TicketStatus status) {
        return tickets.count(policy.visibleTo(actor, status, null, null));
    }

    /** Average in hours, rounded to one decimal; null when there is nothing to average. */
    private static Double average(long totalMinutes, long count) {
        return count == 0 ? null : Math.round(totalMinutes / (double) count / 60.0 * 10.0) / 10.0;
    }
}
