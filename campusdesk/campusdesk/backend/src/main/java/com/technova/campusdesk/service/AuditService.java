package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.AuditResponse;
import com.technova.campusdesk.dto.PageResponse;
import com.technova.campusdesk.entity.AuditLog;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BadRequestException;
import com.technova.campusdesk.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audit trail of administrative operations. Entries are written in the same transaction as the operation. */
@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, User actor, String details) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setActor(actor);
        entry.setDetails(details == null || details.length() <= 500 ? details : details.substring(0, 500));
        logs.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditResponse> search(String action, String entityType, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page must be >= 0 and size must be between 1 and 100");
        }
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                filters.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (entityType != null && !entityType.isBlank()) {
                filters.add(cb.equal(root.get("entityType"), entityType.trim().toUpperCase()));
            }
            return cb.and(filters.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = logs.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(result.map(AuditResponse::from));
    }
}
