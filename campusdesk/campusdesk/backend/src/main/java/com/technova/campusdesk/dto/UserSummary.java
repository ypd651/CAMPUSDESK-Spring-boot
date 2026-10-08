package com.technova.campusdesk.dto;

import com.technova.campusdesk.entity.User;

public record UserSummary(Long id, String fullName, String email) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
