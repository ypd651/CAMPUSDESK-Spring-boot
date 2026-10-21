package com.technova.campusdesk.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes tickets that stayed RESOLVED without an answer from the requester. It runs on a cron schedule and
 * delegates each ticket to TicketService.autoClose, which has its own transaction: one failing ticket never
 * rolls back the others. (The call goes through the Spring proxy because it crosses beans; calling a
 * @Transactional method of the same class would skip the transaction.)
 *
 * app.tickets.auto-close.enabled / days / cron configure it; disable it with enabled=false.
 */
@Component
@ConditionalOnProperty(prefix = "app.tickets.auto-close", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TicketAutoCloseJob {

    private static final Logger log = LoggerFactory.getLogger(TicketAutoCloseJob.class);

    private final TicketService ticketService;
    private final Duration inactivity;

    public TicketAutoCloseJob(TicketService ticketService,
                              @Value("${app.tickets.auto-close.days:3}") long days) {
        this.ticketService = ticketService;
        this.inactivity = Duration.ofDays(days);
    }

    @Scheduled(cron = "${app.tickets.auto-close.cron:0 0 * * * *}")
    public void run() {
        int closed = closeStaleTickets();
        if (closed > 0) {
            log.info("Auto-close job closed {} resolved ticket(s) without a reply", closed);
        }
    }

    /** Returns how many tickets were closed. Public so it can be unit tested without waiting for the cron. */
    public int closeStaleTickets() {
        List<Long> candidates = ticketService.findAutoCloseCandidates(Instant.now().minus(inactivity));
        int closed = 0;
        for (Long id : candidates) {
            try {
                if (ticketService.autoClose(id, inactivity)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not auto-close ticket {}: {}", id, e.getMessage());
            }
        }
        return closed;
    }
}
