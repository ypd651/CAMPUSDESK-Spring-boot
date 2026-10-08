package com.technova.campusdesk.security;

import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.User;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated user carried by the SecurityContext (id, role and permissions of the current request). */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final String roleName;
    private final Set<Permission> permissions;
    private final boolean enabled;

    public UserPrincipal(Long id, String email, String fullName, String passwordHash,
                         String roleName, Set<Permission> permissions, boolean enabled) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.roleName = roleName;
        this.permissions = Set.copyOf(permissions);
        this.enabled = enabled;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getPasswordHash(),
                user.getRole().getName(), user.getRole().getPermissions(), user.isEnabled());
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRoleName() {
        return roleName;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
