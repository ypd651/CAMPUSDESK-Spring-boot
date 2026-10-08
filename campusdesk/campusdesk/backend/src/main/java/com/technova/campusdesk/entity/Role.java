package com.technova.campusdesk.entity;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class Role {

    public static final String ADMIN = "ADMIN";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String USER = "USER";

    @Id
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    /** System roles (ADMIN, TECHNICIAN, USER) cannot be edited or deleted. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_name"))
    @Column(name = "permission", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Permission> permissions = new LinkedHashSet<>();
}
