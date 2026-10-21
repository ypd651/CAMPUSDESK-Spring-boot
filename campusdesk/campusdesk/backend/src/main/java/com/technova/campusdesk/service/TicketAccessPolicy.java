package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.TicketFilter;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Ticket;
import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import com.technova.campusdesk.exception.ForbiddenException;
import com.technova.campusdesk.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for "who may do what with a ticket". The services enforce it and the API
 * exposes the result as availableActions, so the UI and the backend can never disagree.
 *
 * Methods named can* only check the actor (permission + ownership). State rules (for example "only
 * while open") are checked by the services, which report 409 instead of 403 for them.
 */
@Component
public class TicketAccessPolicy {

    public boolean isRequester(UserPrincipal actor, Ticket ticket) {
        return ticket.getRequester().getId().equals(actor.getId());
    }

    public boolean isAssignedTo(UserPrincipal actor, Ticket ticket) {
        return ticket.getTechnician() != null && ticket.getTechnician().getId().equals(actor.getId());
    }

    public boolean canView(UserPrincipal actor, Ticket ticket) {
        if (actor.hasPermission(Permission.TICKET_READ_ALL)) {
            return true;
        }
        if (actor.hasPermission(Permission.TICKET_READ_OWN) && isRequester(actor, ticket)) {
            return true;
        }
        return actor.hasPermission(Permission.TICKET_READ_ASSIGNED) && isAssignedTo(actor, ticket);
    }

    public void requireView(UserPrincipal actor, Ticket ticket) {
        if (!canView(actor, ticket)) {
            throw new ForbiddenException("You are not allowed to access this ticket");
        }
    }

    public boolean canEdit(UserPrincipal actor, Ticket ticket) {
        return actor.hasPermission(Permission.TICKET_EDIT_OWN) && isRequester(actor, ticket);
    }

    public boolean canAssign(UserPrincipal actor) {
        return actor.hasPermission(Permission.TICKET_ASSIGN);
    }

    public boolean canWork(UserPrincipal actor, Ticket ticket) {
        return actor.hasPermission(Permission.TICKET_WORK) && isAssignedTo(actor, ticket);
    }

    public boolean canClose(UserPrincipal actor, Ticket ticket) {
        return actor.hasPermission(Permission.TICKET_CLOSE_OWN) && isRequester(actor, ticket);
    }

    /** The requester or an administrator (TICKET_ASSIGN) can cancel a ticket that has not started. */
    public boolean canCancel(UserPrincipal actor, Ticket ticket) {
        return canClose(actor, ticket) || canAssign(actor);
    }

    public boolean canComment(UserPrincipal actor, Ticket ticket) {
        return actor.hasPermission(Permission.COMMENT_CREATE) && canView(actor, ticket);
    }

    /** Actions the actor can perform right now, taking permissions, ownership and ticket state into account. */
    public List<String> availableActions(UserPrincipal actor, Ticket ticket) {
        List<String> actions = new ArrayList<>();
        TicketStatus status = ticket.getStatus();
        if (canEdit(actor, ticket) && status == TicketStatus.OPEN && ticket.getTechnician() == null) {
            actions.add("EDIT");
        }
        if (canAssign(actor) && (status == TicketStatus.OPEN || status == TicketStatus.ASSIGNED)) {
            actions.add("ASSIGN");
        }
        if (canWork(actor, ticket) && status == TicketStatus.ASSIGNED) {
            actions.add("START");
        }
        if (canWork(actor, ticket) && status == TicketStatus.IN_PROGRESS) {
            actions.add("RESOLVE");
        }
        if (canClose(actor, ticket) && status == TicketStatus.RESOLVED) {
            actions.add("CLOSE");
            actions.add("REOPEN");
        }
        if ((status == TicketStatus.OPEN || status == TicketStatus.ASSIGNED) && canCancel(actor, ticket)) {
            actions.add("CANCEL");
        }
        if (canAssign(actor) && status.isActive()) {
            actions.add("PRIORITY");
        }
        if (canComment(actor, ticket) && status.isActive()) {
            actions.add("COMMENT");
        }
        return actions;
    }

    /** Visibility scope plus the three classic filters (status, priority, category). */
    public Specification<Ticket> visibleTo(UserPrincipal actor, TicketStatus status,
                                           TicketPriority priority, TicketCategory category) {
        return visibleTo(actor, TicketFilter.of(status, priority, category));
    }

    /**
     * Query restriction applied in the database: users with TICKET_READ_ALL see everything; others only
     * their own and/or assigned tickets. Optional filters are combined with AND. Used for lists, search and dashboard.
     */
    public Specification<Ticket> visibleTo(UserPrincipal actor, TicketFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (!actor.hasPermission(Permission.TICKET_READ_ALL)) {
                List<Predicate> scope = new ArrayList<>();
                if (actor.hasPermission(Permission.TICKET_READ_OWN)) {
                    scope.add(cb.equal(root.join("requester", JoinType.LEFT).get("id"), actor.getId()));
                }
                if (actor.hasPermission(Permission.TICKET_READ_ASSIGNED)) {
                    scope.add(cb.equal(root.join("technician", JoinType.LEFT).get("id"), actor.getId()));
                }
                filters.add(scope.isEmpty() ? cb.disjunction() : cb.or(scope.toArray(new Predicate[0])));
            }
            if (filter.status() != null) {
                filters.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                filters.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.category() != null) {
                filters.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.technicianId() != null) {
                filters.add(cb.equal(root.get("technician").get("id"), filter.technicianId()));
            }
            if (Boolean.TRUE.equals(filter.unassigned())) {
                filters.add(cb.isNull(root.get("technician")));
            }
            if (filter.createdFrom() != null) {
                filters.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"),
                        filter.createdFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filter.createdTo() != null) {
                filters.add(cb.lessThan(root.<Instant>get("createdAt"),
                        filter.createdTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filter.text() != null && !filter.text().isBlank()) {
                String pattern = "%" + escapeLike(filter.text().trim().toLowerCase()) + "%";
                filters.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(filters.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
