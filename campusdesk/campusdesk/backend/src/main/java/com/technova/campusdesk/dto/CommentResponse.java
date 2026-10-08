package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.Comment;
import java.time.Instant;

public record CommentResponse(Long id, String content, UserSummary author, Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getId(), comment.getContent(),
                UserSummary.from(comment.getAuthor()), comment.getCreatedAt());
    }
}
