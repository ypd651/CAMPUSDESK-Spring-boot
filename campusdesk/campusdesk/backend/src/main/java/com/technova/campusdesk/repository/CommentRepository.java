package com.technova.campusdesk.repository;

import com.technova.campusdesk.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
