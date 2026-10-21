package com.technova.campusdesk.entity;

/**
 * Ticket life cycle: OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED.
 * Extensions: the requester can reject a resolution (RESOLVED -> IN_PROGRESS) and a ticket that has not
 * started yet can be cancelled (OPEN/ASSIGNED -> CANCELLED). The only allowed transitions are the ones
 * declared here; the backend enforces them.
 */
public enum TicketStatus {
    OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED;

    public boolean canTransitionTo(TicketStatus next) {
        return switch (this) {
            case OPEN -> next == ASSIGNED || next == CANCELLED;
            case ASSIGNED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == RESOLVED;
            case RESOLVED -> next == CLOSED || next == IN_PROGRESS;
            case CLOSED, CANCELLED -> false;
        };
    }

    /** Active tickets can still be commented and worked on; CLOSED and CANCELLED are final. */
    public boolean isActive() {
        return this != CLOSED && this != CANCELLED;
    }
}
