package com.technova.campusdesk.service;

import com.technova.campusdesk.dto.CreateUserRequest;
import com.technova.campusdesk.dto.UpdateUserRequest;
import com.technova.campusdesk.dto.UserResponse;
import com.technova.campusdesk.dto.UserSummary;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.exception.BadRequestException;
import com.technova.campusdesk.exception.BusinessRuleException;
import com.technova.campusdesk.exception.ResourceNotFoundException;
import com.technova.campusdesk.repository.RoleRepository;
import com.technova.campusdesk.repository.UserRepository;
import com.technova.campusdesk.security.UserPrincipal;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::from).toList();
    }

    /** Active users whose role includes TICKET_WORK: the only ones a ticket can be assigned to. */
    @Transactional(readOnly = true)
    public List<UserSummary> technicians() {
        return users.findEnabledWithPermission(Permission.TICKET_WORK).stream().map(UserSummary::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Email is already registered");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(findRole(request.role()));
        user.setEnabled(true);
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, UserPrincipal actor) {
        User target = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean self = target.getId().equals(actor.getId());

        if (request.fullName() != null && !request.fullName().isBlank()) {
            target.setFullName(request.fullName().trim());
        }
        if (request.role() != null) {
            Role newRole = findRole(request.role());
            if (!newRole.getName().equals(target.getRole().getName())) {
                guardPrivilegeLoss(target, self);
                target.setRole(newRole);
            }
        }
        if (request.enabled() != null && request.enabled() != target.isEnabled()) {
            if (!request.enabled()) {
                guardPrivilegeLoss(target, self);
            }
            target.setEnabled(request.enabled());
        }
        return UserResponse.from(users.save(target));
    }

    /** Prevents administrators from locking themselves out or removing the last active administrator. */
    private void guardPrivilegeLoss(User target, boolean self) {
        if (self) {
            throw new BusinessRuleException("You cannot change the role or disable your own account");
        }
        boolean lastAdmin = Role.ADMIN.equals(target.getRole().getName())
                && target.isEnabled()
                && users.countByRole_NameAndEnabledTrue(Role.ADMIN) <= 1;
        if (lastAdmin) {
            throw new BusinessRuleException("At least one active administrator is required");
        }
    }

    private Role findRole(String name) {
        String normalized = name.trim().toUpperCase();
        return roles.findById(normalized)
                .orElseThrow(() -> new BadRequestException("Role '" + normalized + "' does not exist"));
    }
}
