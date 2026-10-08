package com.technova.campusdesk.entity;

/**
 * Catalog of fine-grained permissions. Roles are sets of permissions, so an administrator
 * can create new roles from the UI by combining them. Names intentionally never start with
 * "ROLE_" to avoid clashing with the role authorities used by Spring Security.
 */
public enum Permission {
    TICKET_CREATE("Create support requests"),
    TICKET_READ_OWN("View the tickets the user created"),
    TICKET_READ_ASSIGNED("View the tickets assigned to the user"),
    TICKET_READ_ALL("View every ticket"),
    TICKET_EDIT_OWN("Edit own tickets while they are open and unassigned"),
    TICKET_CLOSE_OWN("Close own tickets once they are resolved"),
    TICKET_ASSIGN("Assign and reassign technicians"),
    TICKET_WORK("Work on assigned tickets (start and resolve); makes the user assignable as technician"),
    COMMENT_CREATE("Add comments to visible active tickets"),
    USERS_READ("View registered users"),
    USERS_MANAGE("Create users and change their role or status"),
    ROLES_MANAGE("Create, edit and delete custom roles");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
