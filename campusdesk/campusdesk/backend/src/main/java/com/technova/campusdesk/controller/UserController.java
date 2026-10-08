package com.technova.campusdesk.controller;

import com.technova.campusdesk.dto.CreateUserRequest;
import com.technova.campusdesk.dto.UpdateUserRequest;
import com.technova.campusdesk.dto.UserResponse;
import com.technova.campusdesk.dto.UserSummary;
import com.technova.campusdesk.security.UserPrincipal;
import com.technova.campusdesk.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User administration and technician listing")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_READ')")
    @Operation(summary = "List all registered users")
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAuthority('TICKET_ASSIGN')")
    @Operation(summary = "List the technicians a ticket can be assigned to")
    public List<UserSummary> technicians() {
        return userService.technicians();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "Create a user with any role (administrator provisioning)")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_MANAGE')")
    @Operation(summary = "Change a user's name, role or enabled status")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal UserPrincipal actor) {
        return userService.update(id, request, actor);
    }
}
