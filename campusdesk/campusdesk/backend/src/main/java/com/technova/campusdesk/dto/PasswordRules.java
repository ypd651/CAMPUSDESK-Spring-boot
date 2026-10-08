package com.technova.campusdesk.dto;

/** Shared password policy: 8-72 characters with lower case, upper case, digit and symbol. */
public final class PasswordRules {

    public static final String PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";
    public static final String MESSAGE =
            "Password must be 8-72 characters and include upper case, lower case, a number and a symbol";

    private PasswordRules() {
    }
}
