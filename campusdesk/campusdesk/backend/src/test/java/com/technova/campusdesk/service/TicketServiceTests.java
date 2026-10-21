package com.technova.campusdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.technova.campusdesk.dto.AssignRequest;
import com.technova.campusdesk.dto.CommentRequest;
import com.technova.campusdesk.dto.StatusChangeRequest;
import com.technova.campusdesk.event.TicketStatusChangedEvent;
import com.technova.campusdesk.dto.TicketRequest;
import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.entity.StatusHistory;
import com.technova.campusdesk.entity.Ticket;
import com.technova.campusdesk.entity.TicketStatus;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BadRequestException;
import com.technova.campusdesk.exception.BusinessRuleException;
import com.technova.campusdesk.exception.ForbiddenException;
import com.technova.campusdesk.repository.CommentRepository;
import com.technova.campusdesk.repository.StatusHistoryRepository;
import com.technova.campusdesk.repository.TicketRepository;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.util.EnumSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests (Mockito): they exercise the business rules of RF-04 without a database. */
class TicketServiceTests {

    private AuditService audit;
    private ApplicationEventPublisher events;
    private TicketRepository tickets;
    private UserRepository users;
    private StatusHistoryRepository history;
    private CommentRepository comments;
    private TicketService ticketService;
    private CommentService commentService;

    private User admin;
    private User technician;
    private User otherTechnician;
    private User requester;
    private User stranger;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        users = mock(UserRepository.class);
        history = mock(StatusHistoryRepository.class);
        comments = mock(CommentRepository.class);
        TicketAccessPolicy policy = new TicketAccessPolicy();
        audit = mock(AuditService.class);
        events = mock(ApplicationEventPublisher.class);
        ticketService = new TicketService(tickets, users, history, policy, audit, events);
        commentService = new CommentService(comments, tickets, users, policy);

        admin = user(1L, Role.ADMIN, EnumSet.allOf(Permission.class));
        technician = user(2L, Role.TECHNICIAN,
                EnumSet.of(Permission.TICKET_READ_ASSIGNED, Permission.TICKET_WORK, Permission.COMMENT_CREATE));
        otherTechnician = user(3L, Role.TECHNICIAN,
                EnumSet.of(Permission.TICKET_READ_ASSIGNED, Permission.TICKET_WORK, Permission.COMMENT_CREATE));
        requester = user(4L, Role.USER, EnumSet.of(Permission.TICKET_CREATE, Permission.TICKET_READ_OWN,
                Permission.TICKET_EDIT_OWN, Permission.TICKET_CLOSE_OWN, Permission.COMMENT_CREATE));
        stranger = user(5L, Role.USER, EnumSet.of(Permission.TICKET_CREATE, Permission.TICKET_READ_OWN,
                Permission.TICKET_EDIT_OWN, Permission.TICKET_CLOSE_OWN, Permission.COMMENT_CREATE));

        for (User u : new User[] {admin, technician, otherTechnician, requester, stranger}) {
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    @Test
    void adminAssignsTechnicianAndTicketBecomesAssignedWithHistory() {   // CP-07
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        var response = ticketService.assign(10L, new AssignRequest(2L), principal(admin));

        assertThat(response.status()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(ticket.getTechnician()).isSameAs(technician);
        verify(history).save(any(StatusHistory.class));
    }

    @Test
    void userCannotAssignTechnicians() {   // CP-08
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.assign(10L, new AssignRequest(2L), principal(requester)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void cannotAssignAUserWhoIsNotATechnician() {
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.assign(10L, new AssignRequest(5L), principal(admin)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminCanReassignAnAssignedTicket() {
        Ticket ticket = ticket(10L, TicketStatus.ASSIGNED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.assign(10L, new AssignRequest(3L), principal(admin));

        assertThat(ticket.getTechnician()).isSameAs(otherTechnician);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void cannotResolveATicketThatWasNeverInProgress() {   // CP-10
        Ticket ticket = ticket(10L, TicketStatus.ASSIGNED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changeStatus(10L,
                new StatusChangeRequest(TicketStatus.RESOLVED, null), principal(technician)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        verify(history, never()).save(any(StatusHistory.class));
    }

    @Test
    void assignedTechnicianMovesTicketForwardAndEachChangeIsRecorded() {   // CP-11
        Ticket ticket = ticket(10L, TicketStatus.ASSIGNED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.IN_PROGRESS, null), principal(technician));
        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.RESOLVED, "Fixed"), principal(technician));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        verify(history, org.mockito.Mockito.times(2)).save(any(StatusHistory.class));
    }

    @Test
    void technicianCannotTouchATicketAssignedToSomeoneElse() {   // CP-09
        Ticket ticket = ticket(10L, TicketStatus.ASSIGNED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changeStatus(10L,
                new StatusChangeRequest(TicketStatus.IN_PROGRESS, null), principal(otherTechnician)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void onlyTheRequesterCanCloseAResolvedTicket() {   // CP-12
        Ticket ticket = ticket(10L, TicketStatus.RESOLVED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changeStatus(10L,
                new StatusChangeRequest(TicketStatus.CLOSED, null), principal(admin)))
                .isInstanceOf(ForbiddenException.class);

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.CLOSED, null), principal(requester));
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void userCannotReadSomeoneElsesTicket() {   // CP-06
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.get(10L, principal(stranger)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void closedTicketsRejectComments() {   // CP-13
        Ticket ticket = ticket(10L, TicketStatus.CLOSED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> commentService.add(10L, new CommentRequest("Late note"), principal(requester)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void statusMachineOnlyAllowsTheDocumentedFlow() {
        assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.ASSIGNED)).isTrue();
        assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED)).isFalse();
        assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.ASSIGNED)).isFalse();
        assertThat(TicketStatus.CLOSED.canTransitionTo(TicketStatus.OPEN)).isFalse();
    }

    // ---- extended rules: reject resolution, cancel, resolvedAt, active-ticket limit ----

    @Test
    void technicianMarksResolvedAndResolvedAtIsStored() {
        Ticket ticket = ticket(10L, TicketStatus.IN_PROGRESS, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.RESOLVED, "Fixed"), principal(technician));

        assertThat(ticket.getResolvedAt()).isNotNull();
    }

    @Test
    void requesterRejectsAResolutionWithANoteAndResolvedAtIsCleared() {
        Ticket ticket = ticket(10L, TicketStatus.RESOLVED, technician);
        ticket.setResolvedAt(java.time.Instant.now());
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.IN_PROGRESS, "Still failing"),
                principal(requester));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getResolvedAt()).isNull();
        verify(history).save(any(StatusHistory.class));
    }

    @Test
    void rejectingAResolutionRequiresANote() {
        Ticket ticket = ticket(10L, TicketStatus.RESOLVED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changeStatus(10L,
                new StatusChangeRequest(TicketStatus.IN_PROGRESS, " "), principal(requester)))
                .isInstanceOf(BadRequestException.class);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void technicianCannotRejectAResolution() {
        Ticket ticket = ticket(10L, TicketStatus.RESOLVED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changeStatus(10L,
                new StatusChangeRequest(TicketStatus.IN_PROGRESS, "Reopening"), principal(technician)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requesterAndAdminCanCancelAnOpenTicketButAStrangerCannot() {
        Ticket mine = ticket(10L, TicketStatus.OPEN, null);
        Ticket other = ticket(11L, TicketStatus.ASSIGNED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(mine));
        when(tickets.findById(11L)).thenReturn(Optional.of(other));

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.CANCELLED, null), principal(requester));
        ticketService.changeStatus(11L, new StatusChangeRequest(TicketStatus.CANCELLED, null), principal(admin));
        assertThat(mine.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(other.getStatus()).isEqualTo(TicketStatus.CANCELLED);

        Ticket third = ticket(12L, TicketStatus.OPEN, null);
        when(tickets.findById(12L)).thenReturn(Optional.of(third));
        assertThatThrownBy(() -> ticketService.changeStatus(12L,
                new StatusChangeRequest(TicketStatus.CANCELLED, null), principal(stranger)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotCancelATicketThatIsAlreadyInProgress() {
        Ticket ticket = ticket(10L, TicketStatus.IN_PROGRESS, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changeStatus(10L,
                new StatusChangeRequest(TicketStatus.CANCELLED, null), principal(requester)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelledTicketsRejectComments() {
        Ticket ticket = ticket(10L, TicketStatus.CANCELLED, null);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> commentService.add(10L, new CommentRequest("Late note"), principal(requester)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void requesterCannotExceedTheLimitOfActiveTickets() {
        when(tickets.countByRequesterIdAndStatusIn(eq(4L), any()))
                .thenReturn((long) TicketService.MAX_ACTIVE_TICKETS_PER_USER);

        assertThatThrownBy(() -> ticketService.create(new TicketRequest("Printer jammed again",
                "The printer on floor 2 jams on every job.", TicketCategory.HARDWARE, TicketPriority.LOW, null),
                principal(requester)))
                .isInstanceOf(BusinessRuleException.class);
        verify(tickets, never()).save(any(Ticket.class));
    }

    @Test
    void searchRejectsUnknownSortFieldsAndInvalidPages() {
        var filter = com.technova.campusdesk.dto.TicketFilter.of(null, null, null);

        assertThatThrownBy(() -> ticketService.search(principal(admin), filter, 0, 10, "password", "asc"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ticketService.search(principal(admin), filter, -1, 10, "createdAt", "asc"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ticketService.search(principal(admin), filter, 0, 500, "createdAt", "asc"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ticketService.search(principal(admin), filter, 0, 10, "createdAt", "sideways"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void adminChangesThePriorityAndItIsAudited() {
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        ticket.setPriority(TicketPriority.LOW);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        var response = ticketService.changePriority(10L,
                new com.technova.campusdesk.dto.PriorityChangeRequest(TicketPriority.CRITICAL, "Production is down"),
                principal(admin));

        assertThat(response.priority()).isEqualTo(TicketPriority.CRITICAL);
        verify(audit).record(eq("TICKET_PRIORITY_CHANGED"), eq("TICKET"), eq(10L), any(), any());
    }

    @Test
    void onlyAdministratorsCanChangeThePriority() {
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        ticket.setPriority(TicketPriority.LOW);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changePriority(10L,
                new com.technova.campusdesk.dto.PriorityChangeRequest(TicketPriority.HIGH, null), principal(requester)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.LOW);
    }

    @Test
    void priorityOfAClosedTicketCannotChange() {
        Ticket ticket = ticket(10L, TicketStatus.CLOSED, technician);
        ticket.setPriority(TicketPriority.LOW);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.changePriority(10L,
                new com.technova.campusdesk.dto.PriorityChangeRequest(TicketPriority.HIGH, null), principal(admin)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void extendedStatusMachine() {
        assertThat(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.IN_PROGRESS)).isTrue();
        assertThat(TicketStatus.OPEN.canTransitionTo(TicketStatus.CANCELLED)).isTrue();
        assertThat(TicketStatus.IN_PROGRESS.canTransitionTo(TicketStatus.CANCELLED)).isFalse();
        assertThat(TicketStatus.CANCELLED.canTransitionTo(TicketStatus.OPEN)).isFalse();
        assertThat(TicketStatus.CLOSED.isActive()).isFalse();
        assertThat(TicketStatus.CANCELLED.isActive()).isFalse();
    }

    // ---- optimistic locking, events and auto-close ----

    @Test
    void staleVersionIsRejectedWithAConflict() {
        Ticket ticket = ticket(10L, TicketStatus.OPEN, null);
        ticket.setVersion(4L);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.assign(10L,
                new AssignRequest(technician.getId(), 3L), principal(admin)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("modified by someone else");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void matchingVersionAllowsTheChange() {
        Ticket ticket = ticket(10L, TicketStatus.ASSIGNED, technician);
        ticket.setVersion(2L);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.IN_PROGRESS, null, 2L),
                principal(technician));

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void statusChangesPublishAnEvent() {
        Ticket ticket = ticket(10L, TicketStatus.ASSIGNED, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        ticketService.changeStatus(10L, new StatusChangeRequest(TicketStatus.IN_PROGRESS, null), principal(technician));

        verify(events).publishEvent(any(TicketStatusChangedEvent.class));
    }

    @Test
    void autoCloseClosesOldResolvedTicketsAndRecordsWhy() {
        Ticket ticket = ticket(10L, TicketStatus.RESOLVED, technician);
        ticket.setResolvedAt(Instant.now().minus(Duration.ofDays(5)));
        when(tickets.findById(10L)).thenReturn(Optional.of(ticket));

        boolean closed = ticketService.autoClose(10L, Duration.ofDays(3));

        assertThat(closed).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        verify(history).save(any(StatusHistory.class));
        verify(audit).record(eq("TICKET_AUTO_CLOSED"), eq("TICKET"), eq(10L), any(), any());
        verify(events).publishEvent(any(TicketStatusChangedEvent.class));
    }

    @Test
    void autoCloseSkipsRecentOrNotResolvedTickets() {
        Ticket recent = ticket(10L, TicketStatus.RESOLVED, technician);
        recent.setResolvedAt(Instant.now().minus(Duration.ofHours(2)));
        Ticket working = ticket(11L, TicketStatus.IN_PROGRESS, technician);
        when(tickets.findById(10L)).thenReturn(Optional.of(recent));
        when(tickets.findById(11L)).thenReturn(Optional.of(working));

        assertThat(ticketService.autoClose(10L, Duration.ofDays(3))).isFalse();
        assertThat(ticketService.autoClose(11L, Duration.ofDays(3))).isFalse();
        assertThat(recent.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        verify(history, never()).save(any(StatusHistory.class));
    }

    // ---- helpers ----

    private User user(Long id, String roleName, Set<Permission> permissions) {
        Role role = new Role();
        role.setName(roleName);
        role.setPermissions(new java.util.LinkedHashSet<>(permissions));
        User user = new User();
        user.setId(id);
        user.setFullName("User " + id);
        user.setEmail("user" + id + "@technova.local");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private Ticket ticket(Long id, TicketStatus status, User assigned) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTitle("Laptop does not boot");
        ticket.setDescription("The laptop shows a black screen after login.");
        ticket.setStatus(status);
        ticket.setRequester(requester);
        ticket.setTechnician(assigned);
        return ticket;
    }

    private UserPrincipal principal(User user) {
        return UserPrincipal.from(user);
    }
}
