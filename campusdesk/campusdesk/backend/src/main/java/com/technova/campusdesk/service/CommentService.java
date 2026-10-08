package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.CommentRequest;
import com.technova.campusdesk.dto.CommentResponse;
import com.technova.campusdesk.entity.Comment;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Ticket;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BusinessRuleException;
import com.technova.campusdesk.exception.ForbiddenException;
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.repository.CommentRepository;
import com.technova.campusdesk.repository.TicketRepository;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository comments;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final TicketAccessPolicy policy;

    public CommentService(CommentRepository comments, TicketRepository tickets,
                          UserRepository users, TicketAccessPolicy policy) {
        this.comments = comments;
        this.tickets = tickets;
        this.users = users;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long ticketId, UserPrincipal actor) {
        Ticket ticket = loadTicket(ticketId);
        policy.requireView(actor, ticket);
        return comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse add(Long ticketId, CommentRequest request, UserPrincipal actor) {
        Ticket ticket = loadTicket(ticketId);
        policy.requireView(actor, ticket);
        if (!actor.hasPermission(Permission.COMMENT_CREATE)) {
            throw new ForbiddenException("Your role cannot add comments");
        }
        if (!ticket.getStatus().isActive()) {
            throw new BusinessRuleException("Closed tickets cannot receive comments");
        }
        User author = users.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setContent(request.content().trim());
        return CommentResponse.from(comments.save(comment));
    }

    private Ticket loadTicket(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }
}
