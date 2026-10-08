package com.technova.campusdesk.config;

import com.technova.campusdesk.dto.PasswordRules;
import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.Role;
import com.technova.campusdesk.entity.User;
import com.technova.campusdesk.repository.RoleRepository;
import com.technova.campusdesk.repository.UserRepository;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Secure provisioning on startup: creates the system roles and the first administrator from environment
 * variables. Administrators and technicians can never be created through public registration.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties properties;

    public DataInitializer(RoleRepository roles, UserRepository users,
                           PasswordEncoder passwordEncoder, BootstrapProperties properties) {
        this.roles = roles;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole(Role.ADMIN, "Full access to tickets, users, roles and statistics", EnumSet.allOf(Permission.class));
        ensureRole(Role.TECHNICIAN, "Works on the tickets assigned to them",
                EnumSet.of(Permission.TICKET_READ_ASSIGNED, Permission.TICKET_WORK, Permission.COMMENT_CREATE));
        ensureRole(Role.USER, "Creates and follows their own support requests",
                EnumSet.of(Permission.TICKET_CREATE, Permission.TICKET_READ_OWN, Permission.TICKET_EDIT_OWN,
                        Permission.TICKET_CLOSE_OWN, Permission.COMMENT_CREATE));
        ensureAdministrator();
    }

    private void ensureRole(String name, String description, Set<Permission> permissions) {
        Role role = roles.findById(name).orElse(null);
        boolean created = role == null;
        if (created) {
            role = new Role();
            role.setName(name);
            role.setPermissions(new LinkedHashSet<>(permissions));
        }
        role.setDescription(description);
        role.setSystemRole(true);
        if (Role.ADMIN.equals(name)) {
            // New catalog permissions are always granted to ADMIN so the administrator is never locked out.
            role.getPermissions().addAll(permissions);
        }
        roles.save(role);
        if (created) {
            log.info("Created system role {}", name);
        }
    }

    private void ensureAdministrator() {
        BootstrapProperties.Admin admin = properties.admin();
        String email = admin.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        if (!admin.password().matches(PasswordRules.PATTERN)) {
            throw new IllegalStateException("ADMIN_PASSWORD does not meet the password policy: " + PasswordRules.MESSAGE);
        }
        User user = new User();
        user.setFullName(admin.fullName() == null || admin.fullName().isBlank()
                ? "System Administrator" : admin.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setRole(roles.findById(Role.ADMIN).orElseThrow());
        user.setEnabled(true);
        users.save(user);
        log.info("Created initial administrator {}", email);
    }
}
