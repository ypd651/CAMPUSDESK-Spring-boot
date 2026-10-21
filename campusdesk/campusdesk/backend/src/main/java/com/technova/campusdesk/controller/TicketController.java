package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.AssignRequest;
import com.technova.campusdesk.dto.CommentRequest;
import com.technova.campusdesk.dto.CommentResponse;
import com.technova.campusdesk.dto.HistoryResponse;
import com.technova.campusdesk.dto.PageResponse;
import com.technova.campusdesk.dto.PriorityChangeRequest;
import com.technova.campusdesk.dto.StatusChangeRequest;
import com.technova.campusdesk.dto.TicketFilter;
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
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping("/search")
    @Operation(summary = "Paginated, sortable search with combined filters",
            description = "Filters are optional and combined with AND. sort: id, title, status, priority, category, "
                    + "createdAt, updatedAt or resolvedAt. direction: asc or desc. Pages start at 0 (size 1-100). "
                    + "Always limited to the tickets the user may see.")
    public PageResponse<TicketResponse> search(@RequestParam(required = false) TicketStatus status,
                                               @RequestParam(required = false) TicketPriority priority,
                                               @RequestParam(required = false) TicketCategory category,
                                               @RequestParam(required = false) Long technicianId,
                                               @RequestParam(required = false) Boolean unassigned,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(defaultValue = "createdAt") String sort,
                                               @RequestParam(defaultValue = "desc") String direction,
                                               @AuthenticationPrincipal UserPrincipal actor) {
        TicketFilter filter = new TicketFilter(status, priority, category, technicianId, unassigned,
                createdFrom, createdTo, q);
        return ticketService.search(actor, filter, page, size, sort, direction);
    }

    @GetMapping("/my-assigned")
    @PreAuthorize("hasAuthority('TICKET_READ_ASSIGNED')")
    @Operation(summary = "Tickets assigned to the current user (technician work queue)")
    public List<TicketResponse> myAssigned(@RequestParam(required = false) TicketStatus status,
                                           @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.myAssigned(actor, status);
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasAuthority('TICKET_ASSIGN')")
    @Operation(summary = "Open tickets without technician (administrator work queue)")
    public List<TicketResponse> unassigned(@AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.unassigned(actor);
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

    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAuthority('TICKET_ASSIGN')")
    @Operation(summary = "Change the priority of an active ticket (administrators)")
    public TicketResponse changePriority(@PathVariable Long id, @Valid @RequestBody PriorityChangeRequest request,
                                         @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.changePriority(id, request, actor);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change the status (technician: IN_PROGRESS, RESOLVED; requester: CLOSED, reject a resolution with a note, CANCELLED)")
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

    @DeleteMapping("/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your own comment while the ticket is active")
    public void deleteComment(@PathVariable Long id, @PathVariable Long commentId,
                              @AuthenticationPrincipal UserPrincipal actor) {
        commentService.delete(id, commentId, actor);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Status change history of a ticket")
    public List<HistoryResponse> history(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        return ticketService.history(id, actor);
    }
}
