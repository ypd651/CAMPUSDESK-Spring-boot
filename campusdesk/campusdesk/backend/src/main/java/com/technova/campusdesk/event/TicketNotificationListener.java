package com.technova.campusdesk.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to status changes AFTER the transaction committed (so nothing is announced for a rolled back change)
 * and on a separate thread (@Async), so a slow notification never delays the HTTP response.
 * Notifications are written to the log; replacing the log call with a mail sender is the only change needed
 * to send real e-mails.
 */
@Component
public class TicketNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TicketNotificationListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(TicketStatusChangedEvent event) {
        // The requester is told about changes made by someone else, and always about automatic ones.
        if (event.automatic() || !event.requesterId().equals(event.changedById())) {
            notifyPerson(event.requesterName(), event.requesterEmail(), event);
        }
        // The technician is told about changes made by someone else (assignment, cancellation, closing...).
        if (event.technicianId() != null && !event.technicianId().equals(event.changedById())) {
            notifyPerson(event.technicianName(), event.technicianEmail(), event);
        }
    }

    private void notifyPerson(String name, String email, TicketStatusChangedEvent event) {
        log.info("[NOTIFICATION] To {} <{}>: ticket #{} \"{}\" went from {} to {} ({}).",
                name, email, event.ticketId(), event.title(), event.previousStatus(), event.newStatus(),
                event.automatic() ? "automatic" : "by " + event.changedByName());
    }
}
