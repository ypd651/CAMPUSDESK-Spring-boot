package com.technova.campusdesk.repository;

import com.technova.campusdesk.entity.Permission;
import com.technova.campusdesk.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByOrderByFullNameAsc();

    long countByRole_Name(String roleName);

    long countByRole_NameAndEnabledTrue(String roleName);

    /** Enabled users whose role carries the given permission (used to list assignable technicians). */
    @Query("select u from User u join u.role r where u.enabled = true and :permission member of r.permissions order by u.fullName")
    List<User> findEnabledWithPermission(@Param("permission") Permission permission);
}
