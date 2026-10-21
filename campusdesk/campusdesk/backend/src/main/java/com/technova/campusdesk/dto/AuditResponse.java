package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.AuditLog;
import java.time.Instant;

public record AuditResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        UserSummary actor,
        String details,
        Instant createdAt) {

    public static AuditResponse from(AuditLog log) {
        return new AuditResponse(log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                UserSummary.from(log.getActor()), log.getDetails(), log.getCreatedAt());
    }
}
