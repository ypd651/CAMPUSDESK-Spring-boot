package com.technova.campusdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "Comment content is required")
        @Size(max = 2000, message = "Comment must have at most 2000 characters")
        String content) {
}
