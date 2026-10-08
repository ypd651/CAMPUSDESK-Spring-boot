package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.PermissionResponse;
import com.technova.campusdesk.dto.RoleRequest;
import com.technova.campusdesk.dto.RoleResponse;
import com.technova.campusdesk.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Custom roles built from the permission catalog")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLES_MANAGE','USERS_MANAGE','USERS_READ')")
    @Operation(summary = "List roles with their permissions and user count")
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    @Operation(summary = "Permission catalog available when creating roles")
    public List<PermissionResponse> permissions() {
        return roleService.permissionCatalog();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    @Operation(summary = "Create a custom role")
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    @Operation(summary = "Update the description and permissions of a custom role")
    public RoleResponse update(@PathVariable String name, @Valid @RequestBody RoleRequest request) {
        return roleService.update(name, request);
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    @Operation(summary = "Delete a custom role that has no users")
    public void delete(@PathVariable String name) {
        roleService.delete(name);
    }
}
