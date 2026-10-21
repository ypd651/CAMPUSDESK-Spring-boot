package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.AssignRequest;
import com.technova.campusdesk.dto.HistoryResponse;
import com.technova.campusdesk.dto.PageResponse;
import com.technova.campusdesk.dto.PriorityChangeRequest;
import com.technova.campusdesk.dto.StatusChangeRequest;
import com.technova.campusdesk.dto.TicketFilter;
import com.technova.campusdesk.dto.TicketRequest;
import com.technova.campusdesk.dto.TicketResponse;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.StatusHistory;
import com.technova.campusdesk.entity.Ticket;
import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BadRequestException;
import com.technova.campusdesk.exception.BusinessRuleException;
import com.technova.campusdesk.exception.ForbiddenException;
import com.technova.campusdesk.event.TicketStatusChangedEvent;
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.repository.StatusHistoryRepository;
import com.technova.campusdesk.repository.TicketRepository;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket life cycle and its business rules. Every state change and its history entry share one transaction. */
@Service
public class TicketService {

    /** A requester cannot have more than this number of unfinished tickets at the same time. */
    public static final int MAX_ACTIVE_TICKETS_PER_USER = 10;

    private static final Set<TicketStatus> ACTIVE_STATUSES = EnumSet.of(
            TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);

    /** Fields the API allows to sort by (anything else is a 400, never a database error). */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "title", "status", "priority", "category", "createdAt", "updatedAt", "resolvedAt", "dueDate");

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final TicketRepository tickets;
    private final UserRepository users;
    private final StatusHistoryRepository historyRepository;
    private final TicketAccessPolicy policy;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public TicketService(TicketRepository tickets, UserRepository users,
                         StatusHistoryRepository historyRepository, TicketAccessPolicy policy,
                         AuditService audit, ApplicationEventPublisher events) {
        this.tickets = tickets;
        this.users = users;
        this.historyRepository = historyRepository;
        this.policy = policy;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(UserPrincipal actor, TicketStatus status,
                                     TicketPriority priority, TicketCategory category) {
        return tickets.findAll(policy.visibleTo(actor, status, priority, category), NEWEST_FIRST).stream()
                .map(ticket -> toResponse(actor, ticket))
                .toList();
    }

    /** Paginated and sorted search with combined filters, always restricted to what the actor may see. */
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> search(UserPrincipal actor, TicketFilter filter,
                                               int page, int size, String sortBy, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page must be >= 0 and size must be between 1 and 100");
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE_FIELDS);
        }
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("direction must be 'asc' or 'desc'");
        }
        Sort sort = Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        Page<Ticket> result = tickets.findAll(policy.visibleTo(actor, filter), PageRequest.of(page, size, sort));
        return PageResponse.of(result.map(ticket -> toResponse(actor, ticket)));
    }

    /** Tickets assigned to the actor (technician work queue). */
    @Transactional(readOnly = true)
    public List<TicketResponse> myAssigned(UserPrincipal actor, TicketStatus status) {
        TicketFilter filter = new TicketFilter(status, null, null, actor.getId(), null, null, null, null);
        return tickets.findAll(policy.visibleTo(actor, filter), NEWEST_FIRST).stream()
                .map(ticket -> toResponse(actor, ticket))
                .toList();
    }

    /** Open tickets that still have no technician (administrator work queue). */
    @Transactional(readOnly = true)
    public List<TicketResponse> unassigned(UserPrincipal actor) {
        TicketFilter filter = new TicketFilter(TicketStatus.OPEN, null, null, null, true, null, null, null);
        return tickets.findAll(policy.visibleTo(actor, filter), Sort.by(Sort.Direction.ASC, "createdAt", "id")).stream()
                .map(ticket -> toResponse(actor, ticket))
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(Long id, UserPrincipal actor) {
        Ticket ticket = load(id);
        policy.requireView(actor, ticket);
        return toResponse(actor, ticket);
    }

    @Transactional
    public TicketResponse create(TicketRequest request, UserPrincipal actor) {
        if (!actor.hasPermission(Permission.TICKET_CREATE)) {
            throw new ForbiddenException("Your role cannot create tickets");
        }
        User requester = loadUser(actor.getId());
        long active = tickets.countByRequesterIdAndStatusIn(requester.getId(), ACTIVE_STATUSES);
        if (active >= MAX_ACTIVE_TICKETS_PER_USER) {
            throw new BusinessRuleException("You already have " + MAX_ACTIVE_TICKETS_PER_USER
                    + " unfinished tickets. Close or cancel one before opening another");
        }

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setDueDate(request.dueDate());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setRequester(requester);
        tickets.save(ticket);

        recordHistory(ticket, null, TicketStatus.OPEN, requester, "Ticket created");
        return toResponse(actor, ticket);
    }

    @Transactional
    public TicketResponse update(Long id, TicketRequest request, UserPrincipal actor) {
        Ticket ticket = load(id);
        policy.requireView(actor, ticket);
        if (!policy.canEdit(actor, ticket)) {
            throw new ForbiddenException("Only the requester can edit this ticket");
        }
        checkVersion(ticket, request.version());
        if (ticket.getStatus() != TicketStatus.OPEN || ticket.getTechnician() != null) {
            throw new BusinessRuleException("A ticket can only be edited while it is open and unassigned");
        }
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        ticket.setDueDate(request.dueDate());
        tickets.save(ticket);
        return toResponse(actor, ticket);
    }

    /** Administrators can re-prioritise an active ticket; the change is written to the audit trail. */
    @Transactional
    public TicketResponse changePriority(Long id, PriorityChangeRequest request, UserPrincipal actor) {
        if (!policy.canAssign(actor)) {
            throw new ForbiddenException("Only administrators can change the priority of a ticket");
        }
        Ticket ticket = load(id);
        checkVersion(ticket, request.version());
        if (!ticket.getStatus().isActive()) {
            throw new BusinessRuleException("The priority of a closed or cancelled ticket cannot be changed");
        }
        TicketPriority before = ticket.getPriority();
        if (before == request.priority()) {
            throw new BusinessRuleException("The ticket already has priority " + before);
        }
        ticket.setPriority(request.priority());
        tickets.save(ticket);

        String note = clean(request.note());
        audit.record("TICKET_PRIORITY_CHANGED", "TICKET", ticket.getId(), loadUser(actor.getId()),
                before + " -> " + request.priority() + (note == null ? "" : ": " + note));
        return toResponse(actor, ticket);
    }

    /** Assigns (OPEN -> ASSIGNED) or reassigns (ASSIGNED -> ASSIGNED) a technician. Administrators only. */
    @Transactional
    public TicketResponse assign(Long id, AssignRequest request, UserPrincipal actor) {
        if (!policy.canAssign(actor)) {
            throw new ForbiddenException("Only administrators can assign technicians");
        }
        Ticket ticket = load(id);
        checkVersion(ticket, request.version());
        if (ticket.getStatus() != TicketStatus.OPEN && ticket.getStatus() != TicketStatus.ASSIGNED) {
            throw new BusinessRuleException("Only open or assigned tickets can be assigned");
        }

        User technician = users.findById(request.technicianId())
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found"));
        if (!technician.isEnabled() || !technician.getRole().getPermissions().contains(Permission.TICKET_WORK)) {
            throw new BadRequestException("The selected user is not an active technician");
        }
        User previous = ticket.getTechnician();
        if (previous != null && previous.getId().equals(technician.getId())) {
            throw new BusinessRuleException("The ticket is already assigned to this technician");
        }

        User admin = loadUser(actor.getId());
        TicketStatus before = ticket.getStatus();
        ticket.setTechnician(technician);
        ticket.setStatus(TicketStatus.ASSIGNED);
        tickets.save(ticket);

        String note = before == TicketStatus.OPEN
                ? "Assigned to " + technician.getFullName()
                : "Reassigned from " + previous.getFullName() + " to " + technician.getFullName();
        recordHistory(ticket, before, TicketStatus.ASSIGNED, admin, note);
        audit.record(before == TicketStatus.OPEN ? "TICKET_ASSIGNED" : "TICKET_REASSIGNED",
                "TICKET", ticket.getId(), admin, note);
        publishStatusChange(ticket, before, TicketStatus.ASSIGNED, admin, false);
        return toResponse(actor, ticket);
    }

    /**
     * Technician: ASSIGNED -> IN_PROGRESS -> RESOLVED.
     * Requester: RESOLVED -> CLOSED, or RESOLVED -> IN_PROGRESS (rejects the resolution, a note is required).
     * Requester or administrator: OPEN/ASSIGNED -> CANCELLED.
     * An impossible transition is a 409; a valid transition by the wrong person is a 403.
     */
    @Transactional
    public TicketResponse changeStatus(Long id, StatusChangeRequest request, UserPrincipal actor) {
        Ticket ticket = load(id);
        policy.requireView(actor, ticket);
        checkVersion(ticket, request.version());

        TicketStatus current = ticket.getStatus();
        TicketStatus target = request.status();
        if (target == TicketStatus.ASSIGNED || !current.canTransitionTo(target)) {
            throw new BusinessRuleException("Transition from " + current + " to " + target + " is not allowed");
        }
        String note = clean(request.note());
        switch (target) {
            case IN_PROGRESS -> {
                if (current == TicketStatus.RESOLVED) {
                    if (!policy.canClose(actor, ticket)) {
                        throw new ForbiddenException("Only the requester can reject the resolution of this ticket");
                    }
                    if (note == null) {
                        throw new BadRequestException("A note explaining why the resolution is rejected is required");
                    }
                } else if (!policy.canWork(actor, ticket)) {
                    throw new ForbiddenException("Only the assigned technician can change this ticket to " + target);
                }
            }
            case RESOLVED -> {
                if (!policy.canWork(actor, ticket)) {
                    throw new ForbiddenException("Only the assigned technician can change this ticket to " + target);
                }
            }
            case CLOSED -> {
                if (!policy.canClose(actor, ticket)) {
                    throw new ForbiddenException("Only the requester can close this ticket");
                }
            }
            case CANCELLED -> {
                if (!policy.canCancel(actor, ticket)) {
                    throw new ForbiddenException("Only the requester or an administrator can cancel this ticket");
                }
            }
            default -> throw new BusinessRuleException("Transition to " + target + " is not allowed");
        }

        User changedBy = loadUser(actor.getId());
        ticket.setStatus(target);
        if (target == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
        } else if (current == TicketStatus.RESOLVED && target == TicketStatus.IN_PROGRESS) {
            ticket.setResolvedAt(null);
        }
        tickets.save(ticket);
        recordHistory(ticket, current, target, changedBy, note);
        if (target == TicketStatus.CANCELLED) {
            audit.record("TICKET_CANCELLED", "TICKET", ticket.getId(), changedBy,
                    note == null ? "Cancelled from " + current : note);
        }
        publishStatusChange(ticket, current, target, changedBy, false);
        return toResponse(actor, ticket);
    }

    /** Ids of RESOLVED tickets that nobody answered since the cutoff (used by the auto-close job). */
    @Transactional(readOnly = true)
    public List<Long> findAutoCloseCandidates(Instant cutoff) {
        return tickets.findIdsByStatusAndResolvedAtBefore(TicketStatus.RESOLVED, cutoff);
    }

    /**
     * Closes a RESOLVED ticket the requester never answered. It runs in its own transaction and re-checks the
     * ticket, so a ticket closed or reopened in the meantime is skipped (returns false). The change is recorded
     * in the history under the requester's name, with a note that makes the automatic closing explicit.
     */
    @Transactional
    public boolean autoClose(Long ticketId, Duration inactivity) {
        Ticket ticket = tickets.findById(ticketId).orElse(null);
        if (ticket == null || ticket.getStatus() != TicketStatus.RESOLVED || ticket.getResolvedAt() == null
                || ticket.getResolvedAt().isAfter(Instant.now().minus(inactivity))) {
            return false;
        }
        User requester = ticket.getRequester();
        ticket.setStatus(TicketStatus.CLOSED);
        tickets.save(ticket);

        String note = "Closed automatically: no reply from the requester in " + inactivity.toDays() + " day(s)";
        recordHistory(ticket, TicketStatus.RESOLVED, TicketStatus.CLOSED, requester, note);
        audit.record("TICKET_AUTO_CLOSED", "TICKET", ticket.getId(), requester, note);
        publishStatusChange(ticket, TicketStatus.RESOLVED, TicketStatus.CLOSED, requester, true);
        return true;
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(Long id, UserPrincipal actor) {
        Ticket ticket = load(id);
        policy.requireView(actor, ticket);
        return historyRepository.findByTicketIdOrderByChangedAtAscIdAsc(id).stream()
                .map(HistoryResponse::from)
                .toList();
    }

    private void recordHistory(Ticket ticket, TicketStatus previous, TicketStatus next, User changedBy, String note) {
        StatusHistory entry = new StatusHistory();
        entry.setTicket(ticket);
        entry.setPreviousStatus(previous);
        entry.setNewStatus(next);
        entry.setChangedBy(changedBy);
        entry.setNote(note);
        historyRepository.save(entry);
    }

    /** Announces a status change. Listeners run after the commit (see TicketNotificationListener). */
    private void publishStatusChange(Ticket ticket, TicketStatus previous, TicketStatus next,
                                     User changedBy, boolean automatic) {
        User requester = ticket.getRequester();
        User technician = ticket.getTechnician();
        events.publishEvent(new TicketStatusChangedEvent(ticket.getId(), ticket.getTitle(), previous, next,
                changedBy.getId(), changedBy.getFullName(),
                requester.getId(), requester.getFullName(), requester.getEmail(),
                technician == null ? null : technician.getId(),
                technician == null ? null : technician.getFullName(),
                technician == null ? null : technician.getEmail(),
                automatic));
    }

    /** Optimistic check done by the application: the client says which version it saw. */
    private void checkVersion(Ticket ticket, Long expected) {
        if (expected != null && !expected.equals(ticket.getVersion())) {
            throw new BusinessRuleException("The ticket was modified by someone else. Reload it and try again");
        }
    }

    private Ticket load(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private User loadUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private TicketResponse toResponse(UserPrincipal actor, Ticket ticket) {
        return TicketResponse.from(ticket, policy.availableActions(actor, ticket));
    }

    private String clean(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
