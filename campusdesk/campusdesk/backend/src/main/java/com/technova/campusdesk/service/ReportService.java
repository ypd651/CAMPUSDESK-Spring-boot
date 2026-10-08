package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.SummaryResponse;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import com.technova.campusdesk.repository.TicketRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.util.LinkedHashMap;
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
                byPriority);
    }

    private long countByStatus(UserPrincipal actor, TicketStatus status) {
        return tickets.count(policy.visibleTo(actor, status, null, null));
    }
}
