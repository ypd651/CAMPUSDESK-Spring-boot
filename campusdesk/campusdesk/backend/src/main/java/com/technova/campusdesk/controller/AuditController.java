package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.AuditResponse;
import com.technova.campusdesk.dto.PageResponse;
import com.technova.campusdesk.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Trail of administrative operations")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "Audit trail, newest first",
            description = "Actions: TICKET_ASSIGNED, TICKET_REASSIGNED, TICKET_CANCELLED, TICKET_PRIORITY_CHANGED, USER_CREATED, "
                    + "USER_ROLE_CHANGED, USER_ENABLED, USER_DISABLED.")
    public PageResponse<AuditResponse> list(@RequestParam(required = false) String action,
                                            @RequestParam(required = false) String entityType,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return auditService.search(action, entityType, page, size);
    }
}
