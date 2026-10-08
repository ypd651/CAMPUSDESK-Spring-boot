package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.PermissionResponse;
import com.technova.campusdesk.dto.RoleRequest;
import com.technova.campusdesk.dto.RoleResponse;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.exception.BusinessRuleException;
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.repository.RoleRepository;
import com.technova.campusdesk.repository.UserRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrators build custom roles by combining catalog permissions. System roles are read-only. */
@Service
public class RoleService {

    private final RoleRepository roles;
    private final UserRepository users;

    public RoleService(RoleRepository roles, UserRepository users) {
        this.roles = roles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roles.findAll().stream()
                .sorted(Comparator.comparing(Role::isSystemRole).reversed().thenComparing(Role::getName))
                .map(this::toResponse)
                .toList();
    }

    public List<PermissionResponse> permissionCatalog() {
        return Arrays.stream(Permission.values()).map(PermissionResponse::from).toList();
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roles.existsById(request.name())) {
            throw new BusinessRuleException("A role with this name already exists");
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description().trim());
        role.setSystemRole(false);
        role.setPermissions(new LinkedHashSet<>(request.permissions()));
        return toResponse(roles.save(role));
    }

    @Transactional
    public RoleResponse update(String name, RoleRequest request) {
        Role role = find(name);
        if (role.isSystemRole()) {
            throw new BusinessRuleException("System roles cannot be modified");
        }
        role.setDescription(request.description().trim());
        role.getPermissions().clear();
        role.getPermissions().addAll(request.permissions());
        return toResponse(roles.save(role));
    }

    @Transactional
    public void delete(String name) {
        Role role = find(name);
        if (role.isSystemRole()) {
            throw new BusinessRuleException("System roles cannot be deleted");
        }
        if (users.countByRole_Name(name) > 0) {
            throw new BusinessRuleException("The role is assigned to users and cannot be deleted");
        }
        roles.delete(role);
    }

    private Role find(String name) {
        return roles.findById(name).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.from(role, users.countByRole_Name(role.getName()));
    }
}
