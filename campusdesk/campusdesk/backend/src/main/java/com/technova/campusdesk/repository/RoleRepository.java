package com.technova.campusdesk.repository;

import com.technova.campusdesk.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {
}
