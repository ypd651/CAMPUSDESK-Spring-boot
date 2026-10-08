package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.AssignRequest;
import com.technova.campusdesk.dto.HistoryResponse;
import com.technova.campusdesk.dto.StatusChangeRequest;
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
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.repository.StatusHistoryRepository;
import com.technova.campusdesk.repository.TicketRepository;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket life cycle and its business rules. Every state change and its history entry share one transaction. */
@Service
public class TicketService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final TicketRepository tickets;
    private final UserRepository users;
    private final StatusHistoryRepository historyRepository;
    private final TicketAccessPolicy policy;

    public TicketService(TicketRepository tickets, UserRepository users,
                         StatusHistoryRepository historyRepository, TicketAccessPolicy policy) {
        this.tickets = tickets;
        this.users = users;
        this.historyRepository = historyRepository;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(UserPrincipal actor, TicketStatus status,
                                     TicketPriority priority, TicketCategory category) {
        return tickets.findAll(policy.visibleTo(actor, status, priority, category), NEWEST_FIRST).stream()
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

        Ticket ticket = new Ticket();
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
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
        if (ticket.getStatus() != TicketStatus.OPEN || ticket.getTechnician() != null) {
            throw new BusinessRuleException("A ticket can only be edited while it is open and unassigned");
        }
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description().trim());
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        tickets.save(ticket);
        return toResponse(actor, ticket);
    }

    /** Assigns (OPEN -> ASSIGNED) or reassigns (ASSIGNED -> ASSIGNED) a technician. Administrators only. */
    @Transactional
    public TicketResponse assign(Long id, AssignRequest request, UserPrincipal actor) {
        if (!policy.canAssign(actor)) {
            throw new ForbiddenException("Only administrators can assign technicians");
        }
        Ticket ticket = load(id);
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
        return toResponse(actor, ticket);
    }

    /**
     * Technician: ASSIGNED -> IN_PROGRESS -> RESOLVED. Requester: RESOLVED -> CLOSED.
     * An impossible transition is a 409; a valid transition by the wrong person is a 403.
     */
    @Transactional
    public TicketResponse changeStatus(Long id, StatusChangeRequest request, UserPrincipal actor) {
        Ticket ticket = load(id);
        policy.requireView(actor, ticket);

        TicketStatus current = ticket.getStatus();
        TicketStatus target = request.status();
        if (target == TicketStatus.ASSIGNED || !current.canTransitionTo(target)) {
            throw new BusinessRuleException("Transition from " + current + " to " + target + " is not allowed");
        }
        switch (target) {
            case IN_PROGRESS, RESOLVED -> {
                if (!policy.canWork(actor, ticket)) {
                    throw new ForbiddenException("Only the assigned technician can change this ticket to " + target);
                }
            }
            case CLOSED -> {
                if (!policy.canClose(actor, ticket)) {
                    throw new ForbiddenException("Only the requester can close this ticket");
                }
            }
            default -> throw new BusinessRuleException("Transition to " + target + " is not allowed");
        }

        User changedBy = loadUser(actor.getId());
        ticket.setStatus(target);
        tickets.save(ticket);
        recordHistory(ticket, current, target, changedBy, clean(request.note()));
        return toResponse(actor, ticket);
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
