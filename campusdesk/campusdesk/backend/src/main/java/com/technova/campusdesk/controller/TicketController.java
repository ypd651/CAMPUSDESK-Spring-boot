package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.AssignRequest;
import com.technova.campusdesk.dto.CommentRequest;
import com.technova.campusdesk.dto.CommentResponse;
import com.technova.campusdesk.dto.HistoryResponse;
import com.technova.campusdesk.dto.StatusChangeRequest;
import com.technova.campusdesk.dto.TicketRequest;
import com.technova.campusdesk.dto.TicketResponse;
import com.technova.campusdesk.entity.TicketCategory;
import com.technova.campusdesk.entity.TicketPriority;
import com.technova.campusdesk.entity.TicketStatus;
import com.technova.campusdesk.security.UserPrincipal;
import com.technova.campusdesk.service.CommentService;
import com.technova.campusdesk.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Thin controller: it only receives the request and delegates every rule to the services. */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Incident life cycle, comments and history")
public class TicketController {

    private final TicketService ticketService;
    private final CommentService commentService;

    public TicketController(TicketService ticketService, CommentService commentService) {
        this.ticketService = ticketService;
        this.commentService = commentService;
    }

    @GetMapping
    @Operation(summary = "List the tickets the user is allowed to see, with optional filters")
    public List<TicketResponse> list(@RequestParam(required = false) TicketStatus status,
                                     @RequestParam(required = false) TicketPriority priority,
                                     @RequestParam(required = false) TicketCategory category,
                                     @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.list(actor, status, priority, category);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ticket detail")
    public TicketResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.get(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a ticket (starts as OPEN)")
    public TicketResponse create(@Valid @RequestBody TicketRequest request, @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.create(request, actor);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a ticket (requester only, while OPEN and unassigned)")
    public TicketResponse update(@PathVariable Long id, @Valid @RequestBody TicketRequest request,
                                 @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.update(id, request, actor);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('TICKET_ASSIGN')")
    @Operation(summary = "Assign or reassign a technician (administrators)")
    public TicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request,
                                 @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.assign(id, request, actor);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change the status (technician: IN_PROGRESS, RESOLVED; requester: CLOSED)")
    public TicketResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request,
                                       @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.changeStatus(id, request, actor);
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comments of a ticket in chronological order")
    public List<CommentResponse> comments(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return commentService.list(id, actor);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment to an active ticket")
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest request,
                                      @AuthenticationPrincipal UserPrincipal actor) {
        return commentService.add(id, request, actor);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Status change history of a ticket")
    public List<HistoryResponse> history(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.history(id, actor);
    }
}
