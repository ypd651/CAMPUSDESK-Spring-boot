package com.technova.campusdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.technova.campusdesk.dto.AssignRequest;
import com.technova.campusdesk.dto.CommentRequest;
import com.technova.campusdesk.dto.StatusChangeRequest;
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests (Mockito): they exercise the business rules of RF-04 without a database. */
class TicketServiceTests {

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
        ticketService = new TicketService(tickets, users, history, policy);
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
